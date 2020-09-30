package de.tum.bgu.msm.util;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class NetworkAnalyser {
    private static final Network network = NetworkUtils.createNetwork();
    private static final Map<String, ArrayList<Link>> possibleBottleneckLinks = new HashMap<>();
    public static final double LENGTH_THRESHOLD = 10000.0;
    private static final int LANES_THRESHOLD = 4;
    private static final double CAPACITY_THRESHOLD = 1800.0;
    private static final double FREESPEED_THRESHOLD = 15;


    private static final String CSV_SEPARATOR = "\t";
    private static final String QUESTIONABLE_LINKS_FILE_NAME = "questionableLinks.csv";

    private static final Logger logger = Logger.getLogger(NetworkAnalyser.class);

    public NetworkAnalyser() {

    }

    private static void readNetworkFile(String path) {
        new MatsimNetworkReader(network).readFile(path);
    }

    private static void checkLength(double threshold) {
        // Check length of each lane, if less than zero or more than threshold the link needs to be checked 
        for (Link link : network.getLinks().values()) {
            if (link.getLength() < 0 || link.getLength() > threshold) {
                if (possibleBottleneckLinks.get("length") == null) {
                    ArrayList<Link> questionableLinks = new ArrayList<>();
                    questionableLinks.add(link);
                    possibleBottleneckLinks.put("length", questionableLinks);
                } else {
                    if (!possibleBottleneckLinks.get("length").contains(link))
                        possibleBottleneckLinks.get("length").add(link);
                }
                logger.info("Adding link #" + link.getId());
            }
        }
    }

    private static void checkLanes(int threshold) {
        // For each link in the network
        for (Link link : network.getLinks().values()) {
            // For each link coming out of the "to" node of the current link
            for (Link nextLink : link.getToNode().getOutLinks().values()) {
                // Check if any of the two links brings congestion
                if (nextLink.getNumberOfLanes() - link.getNumberOfLanes() > threshold) {
                    if (possibleBottleneckLinks.get("lanes") == null) {
                        ArrayList<Link> questionableLinks = new ArrayList<>();
                        questionableLinks.add(link);
                        possibleBottleneckLinks.put("lanes", questionableLinks);
                    } else {
                        if (!possibleBottleneckLinks.get("lanes").contains(link))
                            possibleBottleneckLinks.get("lanes").add(link);
                    }
                    if (!possibleBottleneckLinks.get("length").contains(link))
                    logger.info("Adding link #" + link.getId());
                } else if (link.getNumberOfLanes() - nextLink.getNumberOfLanes() > threshold) {
                    if (possibleBottleneckLinks.get("lanes") == null) {
                        ArrayList<Link> questionableLinks = new ArrayList<>();
                        questionableLinks.add(nextLink);
                        possibleBottleneckLinks.put("lanes", questionableLinks);
                    } else {
                        if (!possibleBottleneckLinks.get("lanes").contains(nextLink))
                            possibleBottleneckLinks.get("lanes").add(nextLink);
                    }
                    logger.info("Adding link #" + link.getId());
                }
            }
        }
    }

    private static void checkCapacity(double threshold) {

    }

    private static void checkFreespeed(double threshold) {

    }

    public static void writeToCsv(String path) {
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8));
            bw.write("Issue, Id, From, To, Length, Capacity, FreeSpeed, Modes, Lanes, Flow Capacity");
            bw.newLine();
            for (Map.Entry<String, ArrayList<Link>> entry : possibleBottleneckLinks.entrySet()) {
                for (Link link : entry.getValue()) {

                    logger.info("Writing link #" + link.getId() + " to file");
                    String oneLine = entry.getKey() + CSV_SEPARATOR +
                            link.getId() + CSV_SEPARATOR +
                            link.getFromNode().getId() + CSV_SEPARATOR +
                            link.getToNode().getId() + CSV_SEPARATOR +
                            link.getLength() + CSV_SEPARATOR +
                            link.getCapacity() + CSV_SEPARATOR +
                            link.getFreespeed() + CSV_SEPARATOR +
                            link.getAllowedModes() + CSV_SEPARATOR +
                            link.getNumberOfLanes() + CSV_SEPARATOR +
                            link.getFlowCapacityPerSec() + CSV_SEPARATOR;
                    bw.write(oneLine);
                    bw.newLine();
                }
            }
            bw.flush();
            bw.close();
        } catch (UnsupportedEncodingException | FileNotFoundException e) {
            logger.error("Encoding/File not found exception");
        } catch (IOException e) {
            logger.error("I/O Error at the .csv file");
        }
    }

    public static void main(String[] args) {
        logger.info("Reading network");
        // First argument contains the network file
        readNetworkFile(args[0]);
        logger.info("Checking links' length");
        checkLength(LENGTH_THRESHOLD);
        logger.info("Checking number of lanes");
        checkLanes(LANES_THRESHOLD);
        logger.info("Writing results to .csv");
        // Second argument contains the output folder
        writeToCsv(args[1] + QUESTIONABLE_LINKS_FILE_NAME);
    }
}
