package com.gaia3d.modifier.billboard.merge;

public class MergeEvaluation {
    boolean mergeable;
    double score;
    double normalDot;
    double planeDistance;
    double efficiency;
    double thickness;
    MergeProjection projection;

    static MergeEvaluation fail() {
        MergeEvaluation evaluation = new MergeEvaluation();
        evaluation.mergeable = false;
        evaluation.score = -1.0;
        return evaluation;
    }
}
