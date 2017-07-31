package de.tum.bgu.msm.data;

import java.io.Serializable;

/**
 * Holds person objects for the Microsimulation Transport Orchestrator (MITO)
 * @author Rolf Moeckel
 * Created on June 8, 2017 in Munich, Germany
 *
 */

public class MitoPerson implements Serializable {

    private final int id;
    private int hhId;
    private int occupation;
    private int workplace;
    private int workzone;

    public MitoPerson(int id, int hhId, int occupation, int workplace) {
        this.id = id;
        this.hhId = hhId;
        this.occupation = occupation;
        this.workplace = workplace;
    }

    public int getHhId() {
        return hhId;
    }

    public void setHhId(int id) {
        this.hhId = hhId;
    }

    public void setWorkplace(int workplace) {
        this.workplace = workplace;
    }

    public int getWorkplace() {
        return workplace;
    }

    public void setWorkzone(int workzone) {
        this.workzone = workzone;
    }


    public int getOccupation() {
        return occupation;
    }

    public int getWorkzone() {
        return workzone;
    }

    public int getId() {
        return this.id;
    }
}