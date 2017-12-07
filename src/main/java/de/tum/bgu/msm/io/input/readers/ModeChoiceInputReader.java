package de.tum.bgu.msm.io.input.readers;

import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.Zone;
import de.tum.bgu.msm.data.AreaTypeForModeChoice;
import de.tum.bgu.msm.io.input.CSVReader;
import de.tum.bgu.msm.resources.Properties;
import de.tum.bgu.msm.resources.Resources;
import de.tum.bgu.msm.util.MitoUtil;

public class ModeChoiceInputReader extends CSVReader {

    private int distIndex;
    private int zoneIndex;
    private int areaTypeHBWIndex;
    private int areaTypeNHBOIndex;

    public ModeChoiceInputReader(DataSet dataSet) {
        super(dataSet);
    }

    @Override
    protected void processHeader(String[] header) {
        zoneIndex = MitoUtil.findPositionInArray("zoneID", header);
        distIndex = MitoUtil.findPositionInArray("distToTransit", header);
        areaTypeHBWIndex = MitoUtil.findPositionInArray("areaTypeHBW", header);
        areaTypeNHBOIndex = MitoUtil.findPositionInArray("areaTypeNHBO", header);
    }

    @Override
    protected void processRecord(String[] record) {
        int zoneID = Integer.parseInt(record[zoneIndex]);
        float distToTransit = Float.parseFloat(record[distIndex]);
        AreaTypeForModeChoice areaTypeHBW = AreaTypeForModeChoice.valueOf(Integer.parseInt(record[areaTypeHBWIndex]));
        AreaTypeForModeChoice areaTypeNHBO = AreaTypeForModeChoice.valueOf(Integer.parseInt(record[areaTypeNHBOIndex]));
        Zone zone = dataSet.getZones().get(zoneID);
        zone.setDistanceToNearestTransitStop(distToTransit);
        zone.setAreaTypeHBWModeChoice(areaTypeHBW);
        zone.setAreaTypeNHBOModeChoice(areaTypeNHBO);
    }

    @Override
    public void read() {
        super.read(Resources.INSTANCE.getString(Properties.AREA_TYPES_AND_TRANSIT_DISTANCE),",");
    }
}