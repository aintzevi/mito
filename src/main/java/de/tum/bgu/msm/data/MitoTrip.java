package de.tum.bgu.msm.data;

import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds trip objects for the Microsimulation Transport Orchestrator (MITO)
 * @author Rolf Moeckel
 * Created on Mar 26, 2017 in Munich, Germany
 *
 */

public class MitoTrip implements Serializable {
    static Logger logger = Logger.getLogger(MitoTrip.class);

    private int tripId;
    private int householdId;
    private int tripPurpose;
    private int tripOrigin;
    private static final Map<Integer,MitoTrip> tripMap = new HashMap<>();


    public MitoTrip(int tripId, int householdId, int tripPurpose, int origin) {
        // create new MitoTrip
        this.tripId = tripId;
        this.householdId = householdId;
        this.tripPurpose = tripPurpose;
        this.tripOrigin = origin;
        tripMap.put(tripId, this);
    }


    public static MitoTrip getTripFromId (int id) {
        return tripMap.get(id);
    }


    public static MitoTrip[] getTripArray() {
        return tripMap.values().toArray(new MitoTrip[tripMap.size()]);
    }


    public static int getTripCount() {
        return tripMap.size();
    }


    public int getTripId() {
        return tripId;
    }

    public int getTripOrigin() {
        return tripOrigin;
    }

    public int getTripPurpose() {
        return tripPurpose;
    }
}