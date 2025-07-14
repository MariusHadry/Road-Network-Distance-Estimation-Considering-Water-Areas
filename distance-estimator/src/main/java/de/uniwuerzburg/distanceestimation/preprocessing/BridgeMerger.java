package de.uniwuerzburg.distanceestimation.preprocessing;

import de.uniwuerzburg.distanceestimation.models.mapInfo.Bridge;
import de.uniwuerzburg.distanceestimation.models.GeoLocation;
import de.uniwuerzburg.distanceestimation.models.mapInfo.WaterArea;
import de.uniwuerzburg.distanceestimation.util.Debug;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.operation.linemerge.LineMerger;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class BridgeMerger {
    private final Set<Bridge> oldParts;
    private final Set<Bridge> alreadyMerged;
    private final WaterArea waterArea;

    public BridgeMerger(Set<Bridge> oldParts, WaterArea waterArea) {
        this.oldParts = oldParts;
        this.waterArea = waterArea;
        alreadyMerged = new HashSet<>();
    }
    public BridgeMerger(Set<Bridge> oldParts) {
        this.oldParts = oldParts;
        waterArea = null;
        alreadyMerged = new HashSet<>();
    }

    public Set<Bridge> merge() {
        Set<Bridge> res = new HashSet<>();
        for (Bridge b : oldParts) {
            if (alreadyMerged.contains(b)) {
                continue;
            }
            try{
                Set<Bridge> fromPaths = mergeFromPath(b, null);
                Set<Bridge> toPaths = mergeToPath(b, null);
                if (fromPaths.isEmpty() && toPaths.isEmpty()) {
                    res.add(b);
                } else if (toPaths.isEmpty()) {
                    for (Bridge other : fromPaths) {
                        LineString full = connectTwoParts(b.geom(), other.geom());
                        res.add(new Bridge(b.name(), full));
                    }
                } else if (fromPaths.isEmpty()) {
                    for (Bridge other : toPaths) {
                        LineString full = connectTwoParts(b.geom(), other.geom());
                        res.add(new Bridge(b.name(), full));
                    }
                } else {
                    for (Bridge from : fromPaths) {
                        for (Bridge to : toPaths) {
                            LineString temp = connectTwoParts(b.geom(), from.geom());
                            LineString full = connectTwoParts(temp, to.geom());
                            res.add(new Bridge(b.name(), full));
                        }
                    }
                }
            } catch (StackOverflowError | IllegalStateException e){
                String name = "";
                if (waterArea != null){
                    name = waterArea.getName();
                }
                Debug.message("Error Merging Bridges: " + name + ", " + b.name() + ", " + b.geom().getCoordinate());
            }

        }
        return res;

    }

    private Set<Bridge> mergeFromPath(Bridge lastPart, Bridge currentBridge) {
        Set<Bridge> newParts = new HashSet<>();
        GeoLocation fromLocation;
        alreadyMerged.add(lastPart);
        if (currentBridge == null) {
            fromLocation = lastPart.getStart();
        } else {
            fromLocation = currentBridge.getStart();
        }

        boolean anyNewMerges = false;
        for (Bridge b : oldParts) {
            if (b.equals(lastPart)) {
                continue;
            }
            if (Objects.equals(fromLocation, b.getEnd()) || Objects.equals(fromLocation, b.getStart())) {
                Bridge newCurrentBridge;
                if (Objects.equals(fromLocation, b.getEnd())) {
                    if (currentBridge == null) {
                        newCurrentBridge = b;
                    } else {
                        LineString mergedLine = connectTwoParts(currentBridge.geom(), b.geom());
                        newCurrentBridge = new Bridge(currentBridge.name(), mergedLine);
                    }
                } else {
                    if (currentBridge == null) {
                        newCurrentBridge = new Bridge(lastPart.name(), b.geom());
                    } else {
                        LineString mergedLine = connectTwoParts(currentBridge.geom(), b.geom());
                        newCurrentBridge = new Bridge(currentBridge.name(), mergedLine);
                    }
                }
                anyNewMerges = true;
                newParts.addAll(mergeFromPath(b, newCurrentBridge));
            }
        }
        if (!anyNewMerges) {
            if (currentBridge != null) {
                newParts.add(currentBridge);
            }
        }
        return newParts;
    }

    private Set<Bridge> mergeToPath(Bridge lastPart, Bridge currentBridge) {
        Set<Bridge> newParts = new HashSet<>();
        GeoLocation toIntnr;
        alreadyMerged.add(lastPart);
        if (currentBridge == null) {
            toIntnr = lastPart.getEnd();
        } else {
            toIntnr = currentBridge.getEnd();
        }

        boolean anyNewMerges = false;
        for (Bridge b : oldParts) {
            if (b.equals(lastPart)) {
                continue;
            }
            if (Objects.equals(toIntnr, b.getStart()) || Objects.equals(toIntnr , b.getEnd())) {
                Bridge newCurrentBridge;

                if (Objects.equals(toIntnr, b.getStart())) {
                    if (currentBridge == null) {
                        newCurrentBridge = b;
                    } else {
                        LineString mergedLine = connectTwoParts(currentBridge.geom(), b.geom());
                        newCurrentBridge = new Bridge(currentBridge.name(), mergedLine);
                    }
                } else {
                    if (currentBridge == null) {
                        newCurrentBridge = new Bridge(lastPart.name(), b.geom());
                    } else {
                        LineString mergedLine = connectTwoParts(currentBridge.geom(), b.geom());
                        newCurrentBridge = new Bridge(currentBridge.name(), mergedLine);
                    }
                }
                anyNewMerges = true;
                newParts.addAll(mergeToPath(b, newCurrentBridge));
            }
        }
        if (!anyNewMerges) {
            if (currentBridge != null) {
                newParts.add(currentBridge);
            }
        }
        return newParts;
    }

    private LineString connectTwoParts(LineString l1, LineString l2) {
        LineMerger merger = new LineMerger();
        merger.add(l1);
        merger.add(l2);
        Collection<LineString> mergedLineStrings = merger.getMergedLineStrings();
        if (mergedLineStrings.size() != 1) {
            throw new IllegalStateException("Error Merging " + l1 + " and " + l2 + ": too many LineStrings in Collection, " +
                    mergedLineStrings.size());
        }
        for (LineString newLine : mergedLineStrings) {
            return newLine;
        }
        return null;
    }
}
