/*
 * Copyright 2011 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.core.impl.phase.custom;

import java.util.List;

import org.optaplanner.core.api.domain.solution.PlanningSolution;
import org.optaplanner.core.api.score.Score;
import org.optaplanner.core.impl.phase.AbstractPhase;
import org.optaplanner.core.impl.phase.custom.scope.CustomPhaseScope;
import org.optaplanner.core.impl.phase.custom.scope.CustomStepScope;
import org.optaplanner.core.impl.score.director.InnerScoreDirector;
import org.optaplanner.core.impl.solver.scope.DefaultSolverScope;

/**
 * Default implementation of {@link CustomPhase}.
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class DefaultCustomPhase<Solution_> extends AbstractPhase<Solution_> implements CustomPhase<Solution_> {

    protected List<CustomPhaseCommand<Solution_>> customPhaseCommandList;
    protected boolean forceUpdateBestSolution;

    protected boolean assertStepScoreFromScratch = false;

    public void setCustomPhaseCommandList(List<CustomPhaseCommand<Solution_>> customPhaseCommandList) {
        this.customPhaseCommandList = customPhaseCommandList;
    }

    public void setForceUpdateBestSolution(boolean forceUpdateBestSolution) {
        this.forceUpdateBestSolution = forceUpdateBestSolution;
    }

    public void setAssertStepScoreFromScratch(boolean assertStepScoreFromScratch) {
        this.assertStepScoreFromScratch = assertStepScoreFromScratch;
    }

    @Override
    public String getPhaseTypeString() {
        return "Custom";
    }

    // ************************************************************************
    // Worker methods
    // ************************************************************************

    @Override
    public void solve(DefaultSolverScope<Solution_> solverScope) {
        CustomPhaseScope<Solution_> phaseScope = new CustomPhaseScope<>(solverScope);
        phaseStarted(phaseScope);

        CustomStepScope<Solution_> stepScope = new CustomStepScope<>(phaseScope);
        for (CustomPhaseCommand<Solution_> customPhaseCommand : customPhaseCommandList) {
            if (termination.isPhaseTerminated(phaseScope)) {
                break;
            }
            stepStarted(stepScope);
            doStep(stepScope, customPhaseCommand);
            stepEnded(stepScope);
            phaseScope.setLastCompletedStepScope(stepScope);
            stepScope = new CustomStepScope<>(phaseScope);
        }
        phaseEnded(phaseScope);
    }

    public void phaseStarted(CustomPhaseScope<Solution_> phaseScope) {
        super.phaseStarted(phaseScope);
    }

    public void stepStarted(CustomStepScope<Solution_> stepScope) {
        super.stepStarted(stepScope);
    }

    private void doStep(CustomStepScope<Solution_> stepScope, CustomPhaseCommand<Solution_> customPhaseCommand) {
        InnerScoreDirector<Solution_> scoreDirector = stepScope.getScoreDirector();
        customPhaseCommand.changeWorkingSolution(scoreDirector);
        Score score = scoreDirector.calculateScore();
        stepScope.setScore(score);
        if (assertStepScoreFromScratch) {
            stepScope.getPhaseScope().assertWorkingScoreFromScratch(stepScope.getScore(), customPhaseCommand);
        }
        bestSolutionRecaller.processWorkingSolutionDuringStep(stepScope);
    }

    public void stepEnded(CustomStepScope<Solution_> stepScope) {
        super.stepEnded(stepScope);
        boolean bestScoreImproved = stepScope.getBestScoreImproved();
        if (forceUpdateBestSolution && !bestScoreImproved) {
            DefaultSolverScope<Solution_> solverScope = stepScope.getPhaseScope().getSolverScope();
            Solution_ newBestSolution = solverScope.getScoreDirector().cloneWorkingSolution();
            bestSolutionRecaller.updateBestSolution(solverScope, newBestSolution);
        }
        CustomPhaseScope<Solution_> phaseScope = stepScope.getPhaseScope();
        if (logger.isDebugEnabled()) {
            logger.debug("    Custom step ({}), time spent ({}), score ({}), {} best score ({}).",
                    stepScope.getStepIndex(),
                    phaseScope.calculateSolverTimeMillisSpentUpToNow(),
                    stepScope.getScore(),
                    bestScoreImproved ? "new" : (forceUpdateBestSolution ? "forced" : "   "),
                    phaseScope.getBestScore());
        }
    }

    public void phaseEnded(CustomPhaseScope<Solution_> phaseScope) {
        super.phaseEnded(phaseScope);
        phaseScope.endingNow();
        logger.info("Custom phase ({}) ended: time spent ({}), best score ({}),"
                        + " score calculation speed ({}/sec), step total ({}).",
                phaseIndex,
                phaseScope.calculateSolverTimeMillisSpentUpToNow(),
                phaseScope.getBestScore(),
                phaseScope.getPhaseScoreCalculationSpeed(),
                phaseScope.getNextStepIndex());
    }

}
