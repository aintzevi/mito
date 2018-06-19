package de.tum.bgu.msm.modules.trafficAssignment;

import de.tum.bgu.msm.data.DataSet;
import de.tum.bgu.msm.data.travelTimes.MitoMatsimTravelTimes;
import de.tum.bgu.msm.modules.Module;
import de.tum.bgu.msm.resources.Properties;
import de.tum.bgu.msm.resources.Resources;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.leastcostpathtree.LeastCostPathTree;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class TrafficAssignment extends Module {

    private Config matsimConfig;
    private MutableScenario matsimScenario;
    private String outputDirectory = "output/trafficAssignment/";
    private Map<Integer,SimpleFeature> zoneFeatureMap = new HashMap<>();

    public TrafficAssignment(DataSet dataSet) {
        super(dataSet);

    }

    @Override
    public void run() {
        configMatsim();
        createPopulation();
        runMatsim();

    }

    private void configMatsim() {
        matsimConfig = ConfigUtils.createConfig();
        matsimConfig = ConfigureMatsim.configureMatsim(matsimConfig);

        String runId = "mito_assignment";
        matsimConfig.controler().setRunId(runId);
        matsimConfig.controler().setOutputDirectory(outputDirectory + "output/");
        matsimConfig.network().setInputFile(Resources.INSTANCE.getString(Properties.MATSIM_NETWORK_FILE));

        matsimConfig.qsim().setNumberOfThreads(16);
        matsimConfig.global().setNumberOfThreads(16);
        matsimConfig.parallelEventHandling().setNumberOfThreads(16);
        matsimConfig.qsim().setUsingThreadpool(false);

        matsimConfig.controler().setLastIteration(Resources.INSTANCE.getInt(Properties.MATSIM_ITERATIONS));
        matsimConfig.controler().setWritePlansInterval(matsimConfig.controler().getLastIteration());
        matsimConfig.controler().setWriteEventsInterval(matsimConfig.controler().getLastIteration());

        matsimConfig.qsim().setStuckTime(10);
        matsimConfig.qsim().setFlowCapFactor(Double.parseDouble(Resources.INSTANCE.getString(Properties.TRIP_SCALING_FACTOR)));
        matsimConfig.qsim().setStorageCapFactor(Math.pow(Double.parseDouble(Resources.INSTANCE.getString(Properties.TRIP_SCALING_FACTOR)),0.75));
    }

    private void createPopulation() {
        this.zoneFeatureMap = MatsimPopulationGenerator.loadZoneShapeFile();
        Population population = MatsimPopulationGenerator.generateMatsimPopulation(dataSet, matsimConfig, zoneFeatureMap);
        matsimScenario = (MutableScenario) ScenarioUtils.loadScenario(matsimConfig);
        matsimScenario.setPopulation(population);
    }

    private void runMatsim() {
        final Controler controler = new Controler(matsimScenario);
        controler.run();

        //set a MitoMatsim travel time as current travel time
        TravelTime travelTime = controler.getLinkTravelTimes();
        TravelDisutility travelDisutility = controler.getTravelDisutilityFactory().createTravelDisutility(travelTime);

        LeastCostPathTree leastCoastPathTree = new LeastCostPathTree(travelTime, travelDisutility);
        MitoMatsimTravelTimes mitoMatsimTravelTimes = new MitoMatsimTravelTimes();
        mitoMatsimTravelTimes.updateTravelTimesFromMatsim(travelTime, travelDisutility, zoneFeatureMap, matsimScenario.getNetwork(), controler.getTripRouterProvider().get() );


        dataSet.setTravelTimes(mitoMatsimTravelTimes);
    }

}
