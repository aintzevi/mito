package de.tum.bgu.msm.io.input.readers;

import com.google.common.math.LongMath;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.MitoZone;
import de.tum.bgu.msm.data.accessTimes.AccessAndEgressVariables;
import de.tum.bgu.msm.data.travelDistances.MatrixTravelDistances;
import de.tum.bgu.msm.data.travelDistances.TravelDistances;
import de.tum.bgu.msm.data.travelTimes.SkimTravelTimes;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;
import de.tum.bgu.msm.resources.Properties;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix2D;
import net.bhl.matsim.uam.analysis.uamroutes.run.RunCalculateUAMRoutes;
import net.bhl.matsim.uam.data.UAMStationConnectionGraph;
import net.bhl.matsim.uam.infrastructure.UAMStation;
import net.bhl.matsim.uam.infrastructure.readers.UAMXMLReader;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads the MATSim uam files (network and station list)
 */
public class UAMNetworkReader {

    private static final Logger logger = Logger.getLogger(UAMNetworkReader.class);
    private final DataSet dataSet;
    private final int TIME_OF_DAY_S = 8 * 60 * 60;
    private final String ACCESS_MODE = "car";
    private Network network;


    public UAMNetworkReader(DataSet dataSet) {
        this.dataSet = dataSet;
    }

    public void read() {

        String networkFileName = Resources.INSTANCE.getString(Properties.MATSIM_NETWORK_FILE);

        Config config = ConfigUtils.createConfig();
        config.network().setInputFile(networkFileName);
        Scenario scenario = ScenarioUtils.loadScenario(config);
        this.network = scenario.getNetwork();
        String uamStationAndVehicleFileName = Resources.INSTANCE.getString(Properties.UAM_VEHICLES);

        UAMXMLReader uamxmlReader = new UAMXMLReader(network);
        uamxmlReader.readFile(uamStationAndVehicleFileName);

        Map<Id<UAMStation>, UAMStation> stationMap = uamxmlReader.getStations();

        //map stations to zones
        Map<UAMStation, MitoZone> stationZoneMap = new HashMap<>();

        for (UAMStation station : stationMap.values()) {
            for (MitoZone zone : dataSet.getZones().values()) {
                SimpleFeature feature = zone.getShapeFeature();
                MultiPolygon polygon = (MultiPolygon) feature.getDefaultGeometry();
                Coord coord = station.getLocationLink().getFromNode().getCoord();
                GeometryFactory factory = polygon.getFactory();
                Point point = factory.createPoint(new Coordinate(coord.getX(), coord.getY()));
                if (polygon.contains(point)) {
                    stationZoneMap.put(station, zone);
                }
            }
        }

        dataSet.setUAMStationAndZoneMap(stationZoneMap);

        //create matrices for the zones that have vertiports:
        IndexedDoubleMatrix2D travelTimeUamAtServedZones = new IndexedDoubleMatrix2D(stationZoneMap.values(), stationZoneMap.values());
        IndexedDoubleMatrix2D travelDistanceUamAtServedZones = new IndexedDoubleMatrix2D(stationZoneMap.values(), stationZoneMap.values());

        //calculate distances and times between all UAM stations
        UAMStationConnectionGraph connections = RunCalculateUAMRoutes.calculateRoutes(network, uamxmlReader);

        for (UAMStation originStation : stationMap.values()) {
            for (UAMStation destinationStation : stationMap.values()) {
                if (originStation.equals(destinationStation))
                    continue;

                // TODO: @Carlos, does this need to include handling time? Let's clarify how MITO deals with waiting+process times of UAM
                travelTimeUamAtServedZones.setIndexed(stationZoneMap.get(originStation).getId(),
                        stationZoneMap.get(destinationStation).getId(),
                        connections.getTravelTime(originStation.getId(), destinationStation.getId()) / 60);

                travelDistanceUamAtServedZones.setIndexed(stationZoneMap.get(originStation).getId(),
                        stationZoneMap.get(destinationStation).getId(),
                        connections.getDistance(originStation.getId(), destinationStation.getId()) / 1000);
            }
        }
        logger.info("The matrix is completed for zones served by UAM");

        //Create matrices for all the zones of the model
        TravelTimes travelTimes = dataSet.getTravelTimes();
        TravelDistances travelDistancesAuto = dataSet.getTravelDistancesAuto();

        IndexedDoubleMatrix2D travelTimeUam = new IndexedDoubleMatrix2D(dataSet.getZones().values(), dataSet.getZones().values());
        IndexedDoubleMatrix2D accessVertiportUam = new IndexedDoubleMatrix2D(dataSet.getZones().values(), dataSet.getZones().values());
        IndexedDoubleMatrix2D egressVertiportUam = new IndexedDoubleMatrix2D(dataSet.getZones().values(), dataSet.getZones().values());
        IndexedDoubleMatrix2D accessDistanceUam = new IndexedDoubleMatrix2D(dataSet.getZones().values(), dataSet.getZones().values());
        IndexedDoubleMatrix2D egressDistanceUam = new IndexedDoubleMatrix2D(dataSet.getZones().values(), dataSet.getZones().values());
        IndexedDoubleMatrix2D accessTimeUam = new IndexedDoubleMatrix2D(dataSet.getZones().values(), dataSet.getZones().values());
        IndexedDoubleMatrix2D egressTimeUam = new IndexedDoubleMatrix2D(dataSet.getZones().values(), dataSet.getZones().values());
        IndexedDoubleMatrix2D travelDistanceUAM = new IndexedDoubleMatrix2D(dataSet.getZones().values(), dataSet.getZones().values());

        int counter = 0;
        for (MitoZone originZone : dataSet.getZones().values()) {
            for (MitoZone destinationZone : dataSet.getZones().values()) {
                counter++;
                int origId = originZone.getId();
                int destId = destinationZone.getId();
                if (!originZone.equals(destinationZone)) {
                    double minTravelTime = 10000;
                    double carTravelTime = travelTimes.getTravelTime(originZone, destinationZone, TIME_OF_DAY_S, ACCESS_MODE);
                    UAMStation accessStationChosen = null;
                    UAMStation egressStationChosen = null;


                    for (UAMStation accessStation : stationMap.values()) {
                        for (UAMStation egressStation : stationMap.values()) {
                            if (!accessStation.equals(egressStation)) {
                                MitoZone accessZone = stationZoneMap.get(accessStation);
                                MitoZone egressZone = stationZoneMap.get(egressStation);
                                double travelTimeAtThisRoute = travelTimes.getTravelTime(originZone, accessZone, TIME_OF_DAY_S, ACCESS_MODE) +
                                        travelTimeUamAtServedZones.getIndexed(accessZone.getId(), egressZone.getId()) +
                                        travelTimes.getTravelTime(egressZone, destinationZone, TIME_OF_DAY_S, ACCESS_MODE);
                                if (travelTimeAtThisRoute < minTravelTime) {
                                    minTravelTime = travelTimeAtThisRoute;
                                    accessStationChosen = accessStation;
                                    egressStationChosen = egressStation;
                                }
                            }
                        }
                    }
                    MitoZone accessZone = stationZoneMap.get(accessStationChosen);
                    MitoZone egressZone = stationZoneMap.get(egressStationChosen);
                    if (minTravelTime < carTravelTime) {
                        travelTimeUam.setIndexed(origId, destId, minTravelTime);

                        accessVertiportUam.setIndexed(origId,
                                destId,
                                accessZone.getId());
                        egressVertiportUam.setIndexed(origId,
                                destId,
                                egressZone.getId());

                        accessTimeUam.setIndexed(origId,
                                destId,
                                travelTimes.getTravelTime(originZone, accessZone, TIME_OF_DAY_S, ACCESS_MODE));
                        egressTimeUam.setIndexed(origId,
                                destId,
                                travelTimes.getTravelTime(egressZone, destinationZone, TIME_OF_DAY_S, ACCESS_MODE));

                        accessDistanceUam.setIndexed(origId,
                                destId,
                                travelDistancesAuto.getTravelDistance(origId, accessZone.getId()));
                        egressDistanceUam.setIndexed(origId,
                                destId,
                                travelDistancesAuto.getTravelDistance(egressZone.getId(), destId));

                        travelDistanceUAM.setIndexed(origId,
                                destId,
                                travelDistanceUamAtServedZones.getIndexed(accessZone.getId(), egressZone.getId()));

                    } else {
                        travelTimeUam.setIndexed(origId, destId, 10000.);
                        accessVertiportUam.setIndexed(origId, destId, 10000);
                        egressVertiportUam.setIndexed(origId, destId, 10000);
                        accessTimeUam.setIndexed(origId, destId, 10000);
                        egressTimeUam.setIndexed(origId, destId, 10000);
                        accessDistanceUam.setIndexed(origId, destId, 10000);
                        egressDistanceUam.setIndexed(origId, destId, 10000);
                        travelDistanceUAM.setIndexed(origId, destId, 10000);
                    }
                } else {
                    travelTimeUam.setIndexed(origId, destId, 10000.);
                    accessVertiportUam.setIndexed(origId, destId, 10000);
                    egressVertiportUam.setIndexed(origId, destId, 10000);
                    accessTimeUam.setIndexed(origId, destId, 10000);
                    egressTimeUam.setIndexed(origId, destId, 10000);
                    accessDistanceUam.setIndexed(origId, destId, 10000);
                    egressDistanceUam.setIndexed(origId, destId, 10000);
                    travelDistanceUAM.setIndexed(origId, destId, 10000);
                }
                if (LongMath.isPowerOfTwo(counter)) {
                    logger.info("Completed " + counter + " origin-destination pairs");
                }
            }
        }
        logger.info("The matrix is completed for all zones");

        //finnally, store the information in the respective containers
        ((SkimTravelTimes) dataSet.getTravelTimes()).updateSkimMatrix(travelTimeUam, "uam");


        dataSet.getAccessAndEgressVariables().
                setExternally(accessVertiportUam, "uam", AccessAndEgressVariables.AccessVariable.ACCESS_VERTIPORT);
        dataSet.getAccessAndEgressVariables().
                setExternally(egressVertiportUam, "uam", AccessAndEgressVariables.AccessVariable.EGRESS_VERTIPORT);

        dataSet.getAccessAndEgressVariables().
                setExternally(accessTimeUam, "uam", AccessAndEgressVariables.AccessVariable.ACCESS_T_MIN);
        dataSet.getAccessAndEgressVariables().
                setExternally(egressTimeUam, "uam", AccessAndEgressVariables.AccessVariable.EGRESS_T_MIN);

        dataSet.getAccessAndEgressVariables().
                setExternally(accessDistanceUam, "uam", AccessAndEgressVariables.AccessVariable.ACCESS_DIST_KM);
        dataSet.getAccessAndEgressVariables().
                setExternally(egressDistanceUam, "uam", AccessAndEgressVariables.AccessVariable.EGRESS_DIST_KM);

        dataSet.setFlyingDistanceUAM(new MatrixTravelDistances(travelDistanceUAM));

    }

    public void printOutSampleForDebugging(String fileName) {
        try {
            PrintWriter pw = new PrintWriter(new File(fileName));
            pw.println("origin,destination,originVertiport,destVertiport,time,accessTime,egressTime,flyingDistance,accessDistance,egressDistance,timeCar,distanceCar");

            for (MitoZone originZone : dataSet.getZones().values()) {
                for (MitoZone destinationZone : dataSet.getZones().values()) {
                    if (originZone.getId() == 1659 || originZone.getId() == 4445 || originZone.getId() == 1) {
                        pw.print(originZone.getId());
                        pw.print(",");
                        pw.print(destinationZone.getId());
                        pw.print(",");
                        pw.print(dataSet.getAccessAndEgressVariables().getAccessVariable(originZone, destinationZone, "uam", AccessAndEgressVariables.AccessVariable.ACCESS_VERTIPORT));
                        pw.print(",");
                        pw.print(dataSet.getAccessAndEgressVariables().getAccessVariable(originZone, destinationZone, "uam", AccessAndEgressVariables.AccessVariable.EGRESS_VERTIPORT));
                        pw.print(",");
                        pw.print(dataSet.getTravelTimes().getTravelTime(originZone, destinationZone, TIME_OF_DAY_S, "uam"));
                        pw.print(",");
                        pw.print(dataSet.getAccessAndEgressVariables().getAccessVariable(originZone, destinationZone, "uam", AccessAndEgressVariables.AccessVariable.ACCESS_T_MIN));
                        pw.print(",");
                        pw.print(dataSet.getAccessAndEgressVariables().getAccessVariable(originZone, destinationZone, "uam", AccessAndEgressVariables.AccessVariable.EGRESS_T_MIN));
                        pw.print(",");
                        pw.print(dataSet.getFlyingDistanceUAM().getTravelDistance(originZone.getId(), destinationZone.getId()));
                        pw.print(",");
                        pw.print(dataSet.getAccessAndEgressVariables().getAccessVariable(originZone, destinationZone, "uam", AccessAndEgressVariables.AccessVariable.ACCESS_DIST_KM));
                        pw.print(",");
                        pw.print(dataSet.getAccessAndEgressVariables().getAccessVariable(originZone, destinationZone, "uam", AccessAndEgressVariables.AccessVariable.EGRESS_DIST_KM));
                        pw.print(",");
                        pw.print(dataSet.getTravelTimes().getTravelTime(originZone, destinationZone, TIME_OF_DAY_S, "car"));
                        pw.print(",");
                        pw.print(dataSet.getTravelDistancesAuto().getTravelDistance(originZone.getId(), destinationZone.getId()));
                        pw.println();
                    }

                }
            }

            pw.close();


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

}