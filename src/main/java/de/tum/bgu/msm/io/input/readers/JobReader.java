package de.tum.bgu.msm.io.input.readers;

import com.vividsolutions.jts.geom.Coordinate;
import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.MitoPerson;
import de.tum.bgu.msm.data.MitoZone;
import de.tum.bgu.msm.data.jobTypes.JobTypeFactory;
import de.tum.bgu.msm.io.input.CSVReader;
import de.tum.bgu.msm.resources.Properties;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.MitoUtil;
import org.apache.log4j.Logger;

/**
 * Created by Nico on 17.07.2017.
 */
public class JobReader extends CSVReader {

    private static final Logger logger = Logger.getLogger(JobReader.class);
    private final JobTypeFactory factory;

    private int posId = -1;
    private int posZone = -1;
    private int posWorker = -1;
    private int posType = -1;
    private int posJobCoordX = -1;
    private int posJobCoordY = -1;

    public JobReader(DataSet dataSet, JobTypeFactory factory) {
        super(dataSet);
        this.factory = factory;
    }

    @Override
    public void read() {
        logger.info("  Reading job micro data from ascii file");
        String fileName = Resources.INSTANCE.getString(Properties.JOBS);
        super.read(fileName, ",");
    }

    @Override
    protected void processHeader(String[] header) {
        posId = MitoUtil.findPositionInArray("id", header);
        posZone = MitoUtil.findPositionInArray("zone", header);
        posWorker = MitoUtil.findPositionInArray("personId", header);
        posType = MitoUtil.findPositionInArray("type", header);
        posJobCoordX = MitoUtil.findPositionInArray("coordX", header);
        posJobCoordY = MitoUtil.findPositionInArray("coordY", header);
    }

    @Override
    protected void processRecord(String[] record) {
        int id = Integer.parseInt(record[posId]);
        int zoneId = Integer.parseInt(record[posZone]);
        int worker = Integer.parseInt(record[posWorker]);
        String type = record[posType];
        if (worker > 0) {
            MitoPerson pp = dataSet.getPersons().get(worker);
            if(pp == null) {
                logger.warn(String.format("Job %d refers to non-existing person %d! Ignoring it.", id, worker));
                return;
            }
            if (pp.getOccupation() != id) {
                logger.warn("Person " + worker + " has workplace " + pp.getOccupation() + " in person file but workplace "
                        + id + " in job file. Ignoring job.");
                return;
            }
            MitoZone zone = dataSet.getZones().get(zoneId);
            if (zone == null) {
                logger.warn(String.format("Job %d refers to non-existing zone %d! Ignoring it.", id, zoneId));
                return;
            }

            try {
                zone.addEmployeeForType(factory.getType(type.toUpperCase()));
            } catch (IllegalArgumentException e) {
                logger.error("Job Type " + type + " used in job microdata but is not defined");
            }
            pp.setOccupationZone(zone);
            pp.setOccupationLocation(new Coordinate(Double.parseDouble(record[posJobCoordX]),
            		Double.parseDouble(record[posJobCoordY])));
        }
    }
}
