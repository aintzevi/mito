package de.tum.bgu.msm.data.travelTimes;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import de.tum.bgu.msm.modules.trafficAssignment.MatsimPopulationGenerator;
import de.tum.bgu.msm.resources.Properties;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.matrices.Matrices;
import omx.OmxFile;
import omx.OmxMatrix;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.TripRouter;
import org.matsim.utils.leastcostpathtree.LeastCostPathTree;
import org.opengis.feature.simple.SimpleFeature;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MitoMatsimTravelTimes implements TravelTimes {

    private final static Logger LOGGER = Logger.getLogger(MitoMatsimTravelTimes.class);
    private SkimTravelTimes delegate = new SkimTravelTimes();
    private LeastCostPathTree leastCoastPathTree;
    private Network network;
    private TripRouter tripRouter;
    private final Map<Integer, List<Node>> zoneCalculationNodesMap = new HashMap<>();
    private final static int NUMBER_OF_CALC_POINTS = 1;
    private final Map<Id<Node>, Map<Double, Map<Id<Node>, LeastCostPathTree.NodeData>>> treesForNodesByTimes = new HashMap<>();

    public void updateTravelTimesFromMatsim(LeastCostPathTree leastCoastPathTree, Map<Integer, SimpleFeature> zoneFeatureMap, Network network, TripRouter tripRouter) {
        this.leastCoastPathTree = leastCoastPathTree;
        this.network = network;
        this.tripRouter = tripRouter;
        this.treesForNodesByTimes.clear();
        updateZoneConnections(zoneFeatureMap);
        updateTransitSkims();
    }

    public void updateTransitSkims(){
        delegate.readSkim("car", Resources.INSTANCE.getString(Properties.AUTO_PEAK_SKIM), "timeByTime", 1/60.);
        delegate.readSkim("bus", Resources.INSTANCE.getString(Properties.BUS_TRAVEL_TIME_SKIM), "mat1", 1.);
        delegate.readSkim("tramMetro", Resources.INSTANCE.getString(Properties.TRAM_METRO_TRAVEL_TIME_SKIM), "mat1", 1.);
        delegate.readSkim("train", Resources.INSTANCE.getString(Properties.TRAIN_TRAVEL_TIME_SKIM), "mat1", 1.);
    }

    private void updateZoneConnections(Map<Integer,SimpleFeature> zoneFeatureMap) {
        zoneCalculationNodesMap.clear();
        for (int zoneId : zoneFeatureMap.keySet()) {
            SimpleFeature originFeature = zoneFeatureMap.get(zoneId);

            for (int i = 0; i < NUMBER_OF_CALC_POINTS; i++) { // Several points in a given origin zone
                Coord originCoord = MatsimPopulationGenerator.getRandomCoordinateInZone(originFeature);
                Node originNode = NetworkUtils.getNearestLink(network, originCoord).getToNode();

                if (!zoneCalculationNodesMap.containsKey(zoneId)) {
                    zoneCalculationNodesMap.put(zoneId, new LinkedList());
                }
                zoneCalculationNodesMap.get(zoneId).add(originNode);
            }
        }
        LOGGER.trace("There are " + zoneCalculationNodesMap.keySet().size() + " origin zones.");
    }

    @Override
    public double getTravelTime(int origin, int destination, double timeOfDay_s, String mode) {
        if (TransportMode.car.equals(mode)){
            double sumTravelTime_min = 0.;

            for (Node originNode : zoneCalculationNodesMap.get(origin)) { // Several points in a given origin zone
                Map<Id<Node>, LeastCostPathTree.NodeData> tree;
                if (treesForNodesByTimes.containsKey(originNode.getId())) {
                    Map<Double, Map<Id<Node>, LeastCostPathTree.NodeData>> treesForOneNodeByTimes = treesForNodesByTimes.get(originNode.getId());
                    if (treesForOneNodeByTimes.containsKey(timeOfDay_s)) {
                        tree = treesForOneNodeByTimes.get(timeOfDay_s);
                    } else {
                        leastCoastPathTree.calculate(network, originNode, timeOfDay_s);
                        tree = leastCoastPathTree.getTree();
                        treesForOneNodeByTimes.put(timeOfDay_s, tree);
                    }
                } else {
                    Map<Double, Map<Id<Node>, LeastCostPathTree.NodeData>> treesForOneNodeByTimes = new HashMap<>();
                    leastCoastPathTree.calculate(network, originNode, timeOfDay_s);
                    tree = leastCoastPathTree.getTree();
                    treesForOneNodeByTimes.put(timeOfDay_s, tree);
                    treesForNodesByTimes.put(originNode.getId(), treesForOneNodeByTimes);
                }

                for (Node destinationNode : zoneCalculationNodesMap.get(destination)) {// several points in a given destination zone

                    double arrivalTime_s = tree.get(destinationNode.getId()).getTime();
                    sumTravelTime_min += ((arrivalTime_s - timeOfDay_s) / 60.);
                }
            }
            return sumTravelTime_min / NUMBER_OF_CALC_POINTS;
        } else {
            return delegate.getTravelTime(origin, destination, timeOfDay_s, mode);
        }
    }
}
