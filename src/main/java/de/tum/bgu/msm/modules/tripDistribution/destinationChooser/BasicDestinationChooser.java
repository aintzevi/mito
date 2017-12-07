package de.tum.bgu.msm.modules.tripDistribution.destinationChooser;

import com.google.common.math.LongMath;
import com.pb.common.matrix.Matrix;
import de.tum.bgu.msm.data.*;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;
import de.tum.bgu.msm.modules.tripDistribution.TripDistribution;
import de.tum.bgu.msm.util.MitoUtil;
import de.tum.bgu.msm.util.concurrent.RandomizableConcurrentFunction;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.log4j.Logger;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class BasicDestinationChooser extends RandomizableConcurrentFunction {

    private final static Logger logger = Logger.getLogger(BasicDestinationChooser.class);

    protected final Purpose purpose;
    protected final EnumMap<Purpose, Matrix> baseProbabilities;
    protected final TravelTimes travelTimes;
    protected final DataSet dataSet;
    protected float[] destinationProbabilities;

    private double ratio = 1;
    private double idealBudgetSum = 0;
    private double actualBudgetSum = 0;
    private double adjustedBudget;
    private double hhBudgetPerTrip;

    private final NormalDistribution distribution = new NormalDistribution(100, 50);
    private final Map<Integer, Double> densityByDeviation = new HashMap<>();

    public BasicDestinationChooser(Purpose purpose, EnumMap<Purpose, Matrix> baseProbabilities, DataSet dataSet) {
        this.dataSet = dataSet;
        this.travelTimes = dataSet.getTravelTimes("car");
        this.purpose = purpose;
        this.baseProbabilities = baseProbabilities;
    }

    @Override
    public void execute() {
        long counter = 0;
        for (MitoHousehold household : dataSet.getHouseholds().values()) {
            if (LongMath.isPowerOfTwo(counter)) {
                logger.info(counter + " households done for Purpose " + purpose);
            }
            if (isValid(household)) {
                updateBaseDestinationProbabilities(household);
                updateBudgets(household);
                updateAdjustedDestinationProbabilities(household);
                for (MitoTrip trip : household.getTripsForPurpose(purpose)) {
                    trip.setTripOrigin(findOrigin(household, trip));
                    trip.setTripDestination(findDestination(trip));
                    postProcessTrip(trip);
                    TripDistribution.DISTRIBUTED_TRIPS_COUNTER.incrementAndGet();
                }
            }
            counter++;
        }
    }

    protected void updateAdjustedDestinationProbabilities(MitoHousehold household){
        adjustDestinationProbabilities(household.getHomeZone().getZoneId());
    }

    protected boolean isValid(MitoHousehold household) {
        return !household.getTripsForPurpose(purpose).isEmpty() && household.getTravelTimeBudgetForPurpose(purpose) > 0.;
    }

    void postProcessTrip(MitoTrip trip) {
        actualBudgetSum += travelTimes.getTravelTime(trip.getTripOrigin().getZoneId(), trip.getTripDestination().getZoneId());
        idealBudgetSum += hhBudgetPerTrip;
    }

    private void updateBaseDestinationProbabilities(MitoHousehold household) {
        destinationProbabilities = baseProbabilities.get(purpose).getRow(household.getHomeZone().getZoneId()).getValues()[0];
    }

    protected void updateBudgets(MitoHousehold household) {
        ratio = (idealBudgetSum / Math.min(1., actualBudgetSum));
        hhBudgetPerTrip = household.getTravelTimeBudgetForPurpose(purpose) / household.getTripsForPurpose(purpose).size();
        adjustedBudget = (hhBudgetPerTrip * ratio) / 100;
    }

    protected Zone findOrigin(MitoHousehold household, MitoTrip trip) {
        return household.getHomeZone();
    }

    protected Zone findDestination(MitoTrip trip) {
        final int destination = baseProbabilities.get(purpose).getExternalNumber(MitoUtil.select(destinationProbabilities, random));
        return dataSet.getZones().get(destination);
    }

    void adjustDestinationProbabilities(int origin) {
        for (int i = 0; i < destinationProbabilities.length; i++) {
            int deviation = (int) ((travelTimes.getTravelTime(origin, baseProbabilities.get(purpose).getExternalNumber(i)) / adjustedBudget));
            destinationProbabilities[i] = destinationProbabilities[i] * (float) getDensity(deviation);
        }
    }

    private double getDensity(int deviation) {
        if (densityByDeviation.containsKey(deviation)) {
            return densityByDeviation.get(deviation);
        } else {
            double density = distribution.density(deviation);
            densityByDeviation.put(deviation, density);
            return density;
        }
    }
}
