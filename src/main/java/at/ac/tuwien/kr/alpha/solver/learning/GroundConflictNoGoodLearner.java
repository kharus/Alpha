/**
 * Copyright (c) 2016-2019, the Alpha Team.
 * All rights reserved.
 *
 * Additional changes made by Siemens.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package at.ac.tuwien.kr.alpha.solver.learning;

import at.ac.tuwien.kr.alpha.common.Assignment;
import at.ac.tuwien.kr.alpha.common.NoGood;
import at.ac.tuwien.kr.alpha.common.NoGoodCreator;
import at.ac.tuwien.kr.alpha.solver.TrailAssignment;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashSet;

import static at.ac.tuwien.kr.alpha.Util.oops;
import static at.ac.tuwien.kr.alpha.common.Literals.atomOf;

/**
 * Copyright (c) 2016, the Alpha Team.
 */
public class GroundConflictNoGoodLearner {
	private static final Logger LOGGER = LoggerFactory.getLogger(GroundConflictNoGoodLearner.class);

	private final Assignment assignment;

	public int computeConflictFreeBackjumpingLevel(NoGood violatedNoGood) {
		int highestDecisionLevel = -1;
		int secondHighestDecisionLevel = -1;
		int numAtomsInHighestLevel = 0;
		for (Integer literal : violatedNoGood) {
			int literalDecisionLevel = assignment.getRealWeakDecisionLevel(atomOf(literal));
			if (literalDecisionLevel == highestDecisionLevel) {
				numAtomsInHighestLevel++;
			} else if (literalDecisionLevel > highestDecisionLevel) {
				secondHighestDecisionLevel = highestDecisionLevel;
				highestDecisionLevel = literalDecisionLevel;
				numAtomsInHighestLevel = 1;
			} else if (literalDecisionLevel > secondHighestDecisionLevel) {
				secondHighestDecisionLevel = literalDecisionLevel;
			}
		}
		if (numAtomsInHighestLevel == 1) {
			return secondHighestDecisionLevel;
		}
		return highestDecisionLevel - 1;
	}

	public static class ConflictAnalysisResult {
		public static final ConflictAnalysisResult UNSAT = new ConflictAnalysisResult();

		public final NoGood learnedNoGood;
		public final int backjumpLevel;
		public final boolean clearLastChoiceAfterBackjump;
		public final Collection<Integer> resolutionAtoms;

		private ConflictAnalysisResult() {
			learnedNoGood = null;
			backjumpLevel = -1;
			clearLastChoiceAfterBackjump = false;
			resolutionAtoms = null;
		}

		public ConflictAnalysisResult(NoGood learnedNoGood, int backjumpLevel, boolean clearLastChoiceAfterBackjump, Collection<Integer> resolutionAtoms) {
			if (backjumpLevel < 0) {
				throw oops("Backjumping level is smaller than 0");
			}

			this.learnedNoGood = learnedNoGood;
			this.backjumpLevel = backjumpLevel;
			this.clearLastChoiceAfterBackjump = clearLastChoiceAfterBackjump;
			this.resolutionAtoms = resolutionAtoms;
		}

		@Override
		public String toString() {
			if (this == UNSAT) {
				return "UNSATISFIABLE";
			}

			return learnedNoGood + "@" + backjumpLevel;
		}
	}

	public GroundConflictNoGoodLearner(Assignment assignment) {
		this.assignment = assignment;
	}

	public ConflictAnalysisResult analyzeConflictingNoGood(NoGood violatedNoGood) {
		LOGGER.trace("Analyzing violated nogood: {}", violatedNoGood);
		return analyzeConflictingNoGoodRepetition(violatedNoGood, new LinkedHashSet<>());
	}

	private ConflictAnalysisResult analyzeConflictingNoGoodRepetition(NoGood violatedNoGood, Collection<Integer> resolutionAtoms) {
		NoGood currentResolutionNoGood = violatedNoGood.withoutHead();	// Clone violated NoGood and remove potential head.
		// Find decision level where conflict occurs (i.e., highest decision level of violatedNoGood).
		int conflictDecisionLevel = -1;
		for (Integer literal : currentResolutionNoGood) {
			Assignment.Entry literalEntry = assignment.get(atomOf(literal));
			int literalDL = literalEntry.getWeakDecisionLevel();
			if (literalDL > conflictDecisionLevel) {
				conflictDecisionLevel = literalDL;
			}
		}
		if (conflictDecisionLevel == 0) {
			// The given set of NoGoods is unsatisfiable (conflict at decisionLevel 0).
			return ConflictAnalysisResult.UNSAT;
		}
		FirstUIPPriorityQueue firstUIPPriorityQueue = new FirstUIPPriorityQueue(conflictDecisionLevel);
		for (Integer literal : currentResolutionNoGood) {
			firstUIPPriorityQueue.add(getAssignmentEntryRespectingLowerMBT(literal));
			//sortLiteralToProcessIntoList(sortedLiteralsToProcess, literal, conflictDecisionLevel);
		}
		// TODO: create ResolutionSequence
		if (firstUIPPriorityQueue.size() == 1) {
			// There is only one literal to process, i.e., only one literal in the violatedNoGood is from conflict decision level.
			// This means that the NoGood already was unit but got violated, because another NoGood propagated earlier or a wrong choice was made.
			// The real conflict therefore is caused by either:
			// a) two NoGoods propagating the same atom to different truth values in the current decisionLevel, or
			// b) a NoGood propagating at a lower decision level to the inverse value of a choice with higher decision level.
			// TODO: b) should no longer be possible, since all propagation is on highest decision level now.
			// For a) we need to work also with the other NoGood.
			// For b) we need to backtrack the wrong choice.

			Assignment.Entry atomAssignmentEntry = firstUIPPriorityQueue.poll();
			NoGood otherContributingNoGood = atomAssignmentEntry.getImpliedByRespectingLowerMBT();
			if (otherContributingNoGood == null) {
				// Case b), the other assignment is a decision.
				return new ConflictAnalysisResult(null, atomAssignmentEntry.getWeakDecisionLevel(), true, resolutionAtoms);
			}
			// Case a) take other implying NoGood into account.
			int resolutionAtom = atomAssignmentEntry.getAtom();
			resolutionAtoms.add(resolutionAtom);
			currentResolutionNoGood = NoGoodCreator.learnt(resolveNoGoods(firstUIPPriorityQueue, currentResolutionNoGood, otherContributingNoGood, resolutionAtom, atomAssignmentEntry.getWeakDecisionLevel(), atomAssignmentEntry.getPropagationLevelRespectingLowerMBT()));
			
			// TODO: create/edit ResolutionSequence
		}

		while (true) {
			// Check if 1UIP was reached.
			if (firstUIPPriorityQueue.size() == 1) {
				// Only one remaining literals to process, we reached 1UIP.
				int backjumpingDecisionLevel = computeBackjumpingDecisionLevel(currentResolutionNoGood);
				if (backjumpingDecisionLevel < 0) {
					return repeatAnalysisIfNotAssigning(currentResolutionNoGood, resolutionAtoms);
				}
				return new ConflictAnalysisResult(currentResolutionNoGood, backjumpingDecisionLevel, false, resolutionAtoms);
			} else if (firstUIPPriorityQueue.size() < 1) {
				// This can happen if some NoGood implied a literal at a higher decision level and later the implying literals become (re-)assigned at lower decision levels.
				// Lowering the decision level may be possible but requires further analysis.
				// For the moment, just report the learned NoGood.
				return repeatAnalysisIfNotAssigning(currentResolutionNoGood, resolutionAtoms);
				//return new ConflictAnalysisResult(currentResolutionNoGood, computeBackjumpingDecisionLevel(currentResolutionNoGood), false, noGoodsResponsible);
			} else if (currentResolutionNoGood.size() > 32) {
				// Break if resolved NoGood becomes too large.
				// Remove all current-dl elements from the resolution NoGood and add the last choice, then backjump like usual.
				return new ConflictAnalysisResult(null, conflictDecisionLevel, true, resolutionAtoms);	// Flag unsatisfiable abused here.
				/*if (getAssignmentEntryRespectingLowerMBT(lastGuessedAtom).getDecisionLevel() <= conflictDecisionLevel) {
					// If lastGuessedAtom is not unassigned after backjump, use repeatAnalysisIfNotAssigning.
					return repeatAnalysisIfNotAssigning(replaceAllFromConflictDecisionLevelWithGuess(currentResolutionNoGood, conflictDecisionLevel, lastGuessedAtom), noGoodsResponsible, lastGuessedAtom);
				}
				return new ConflictAnalysisResult(replaceAllFromConflictDecisionLevelWithGuess(currentResolutionNoGood, conflictDecisionLevel, lastGuessedAtom), conflictDecisionLevel - 1, false, noGoodsResponsible, false, isLearntTooLarge);*/
			}

			// Resolve next NoGood based on current literal
			Assignment.Entry currentLiteralAssignment = firstUIPPriorityQueue.poll();
			int decisionLevel = currentLiteralAssignment.getWeakDecisionLevel();
			int propagationLevel = currentLiteralAssignment.getPropagationLevelRespectingLowerMBT();
			// Get NoGood it was implied by.
			NoGood impliedByNoGood = currentLiteralAssignment.getImpliedByRespectingLowerMBT();
			if (impliedByNoGood == null) {
				// Literal was a decision, keep it in the currentResolutionNoGood by simply skipping.
				continue;
			}
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("ImpliedBy NoGood is: {}.", impliedByNoGood);
				for (Integer literal : impliedByNoGood) {
					LOGGER.debug("Literal assignment: {}={}.", atomOf(literal), assignment.get(atomOf(literal)));
				}
			}
			// TODO: add entry in ResolutionSequence.

			int resolutionAtom = currentLiteralAssignment.getAtom();
			resolutionAtoms.add(resolutionAtom);
			currentResolutionNoGood = NoGoodCreator.learnt(resolveNoGoods(firstUIPPriorityQueue, currentResolutionNoGood, impliedByNoGood, resolutionAtom, decisionLevel, propagationLevel));
		}
	}

	/**
	 * Returns the Assignment.Entry of a literal. If the literal is TRUE but was MBT before, the (previous) MBT entry is returned.
	 * @param literal
	 * @return
	 */
	private Assignment.Entry getAssignmentEntryRespectingLowerMBT(Integer literal) {
		Assignment.Entry literalEntry = assignment.get(atomOf(literal));
		// If current assignment is TRUE and previous was MBT, take previous decision level.
		return literalEntry;
	}

	private ConflictAnalysisResult repeatAnalysisIfNotAssigning(NoGood learnedNoGood, Collection<Integer> resolutionAtoms) {
		int backjumpingDecisionLevel = computeBackjumpingDecisionLevel(learnedNoGood);
		if (backjumpingDecisionLevel < 0) {
			// The learned NoGood is not yet assigning, repeat the learning on the now-highest decision level.
			// This can only be the case if a literal got assigned at a lower decision level, otherwise the learnedNoGood is always assigning.
			return analyzeConflictingNoGoodRepetition(learnedNoGood, resolutionAtoms);
		}
		return new ConflictAnalysisResult(learnedNoGood, backjumpingDecisionLevel, false, resolutionAtoms);
	}

	/**
	 * Resolves two NoGoods and returns the new NoGood as array of literals.
	 * Literals of the second NoGood are sorted into the firstUIPPriorityQueue list.
	 * Literals of the first NoGood are assumed to be already in that queue and not added to it.
	 * @param firstUIPPriorityQueue
	 * @param firstNoGood
	 * @param secondNoGood
	 * @return
	 */
	private int[] resolveNoGoods(FirstUIPPriorityQueue firstUIPPriorityQueue, NoGood firstNoGood, NoGood secondNoGood, int resolutionAtom, int resolutionLiteralDecisionLevel, int resolutionLiteralPropagationLevel) {
		// Resolve implied nogood into current resolution.
		int resolvedLiterals[] = new int[secondNoGood.size() + firstNoGood.size() - 2];
		int resolvedCounter = 0;
		// Copy over all literals except the resolving ones.
		for (int i = 0; i < firstNoGood.size(); i++) {
			if (atomOf(firstNoGood.getLiteral(i)) != resolutionAtom) {
				resolvedLiterals[resolvedCounter++] = firstNoGood.getLiteral(i);
			}
		}
		// Copy literals from implying nogood except the resolving one and sort additional literals into processing list.
		for (int i = 0; i < secondNoGood.size(); i++) {
			if (atomOf(secondNoGood.getLiteral(i)) != resolutionAtom) {
				resolvedLiterals[resolvedCounter++] = secondNoGood.getLiteral(i);

				// Sort literal also into queue for further processing.
				Assignment.Entry newLiteral = getAssignmentEntryRespectingLowerMBT(secondNoGood.getLiteral(i));
				if (assignment instanceof TrailAssignment) {
					firstUIPPriorityQueue.add(newLiteral);
					continue;
				}
				// Check for special case where literal was assigned from MBT to TRUE on the same decisionLevel and the propagationLevel of TRUE is higher than the one of the resolutionLiteral.
				if (newLiteral.getDecisionLevel() == resolutionLiteralDecisionLevel
					&& newLiteral.getPropagationLevel() > resolutionLiteralPropagationLevel) {
					if (newLiteral.hasPreviousMBT()
						&& newLiteral.getMBTDecisionLevel() == resolutionLiteralDecisionLevel
						&& newLiteral.getMBTPropagationLevel() < resolutionLiteralPropagationLevel) {
						// Resort to the previous entry (i.e., the one for MBT) and use that one.
						firstUIPPriorityQueue.add(newLiteral);
						continue;
					}
					throw oops("Implying literal on current decisionLevel has higher propagationLevel than the implied literal and this was no assignment from MBT to TRUE");
				}
				// Add literal to queue for finding 1UIP.
				firstUIPPriorityQueue.add(newLiteral);
			}
		}
		return resolvedLiterals;
	}

	public ResolutionSequence obtainResolutionSequence() {
		throw new NotImplementedException("Method not yet implemented.");
	}

	/**
	 * Compute the backjumping decision level, i.e., the decision level on which the learned NoGood is assigning (NoGood is unit and propagates).
	 * This usually is the second highest decision level occurring in the learned NoGood, but due to assignments of MBT no such decision level may exist.
	 * @param learnedNoGood
	 * @return -1 if there is no decisionLevel such that backjumping to it makes the learnedNoGood unit.
	 */
	private int computeBackjumpingDecisionLevel(NoGood learnedNoGood) {
		LOGGER.trace("Computing backjumping decision level for {}.", learnedNoGood);
		int highestDecisionLevel = -1;
		int secondHighestDecisionLevel = -1;
		int numLiteralsOfHighestDecisionLevel = -1;
		if (learnedNoGood.isUnary()) {
			// Singleton NoGoods induce a backjump to the decision level before the NoGood got violated.
			int singleLiteralDecisionLevel = assignment.get(atomOf(learnedNoGood.getLiteral(0))).getDecisionLevel();
			if (assignment instanceof TrailAssignment) {
				singleLiteralDecisionLevel = Math.min(singleLiteralDecisionLevel, ((TrailAssignment) assignment).getOutOfOrderDecisionLevel(learnedNoGood.getPositiveLiteral(0)));
			}
			int singleLiteralBackjumpingLevel = singleLiteralDecisionLevel - 1 >= 0 ? singleLiteralDecisionLevel - 1 : 0;
			LOGGER.trace("NoGood has only one literal, backjumping to level: {}", singleLiteralBackjumpingLevel);
			return singleLiteralBackjumpingLevel;
		}
		for (Integer integer : learnedNoGood) {
			Assignment.Entry assignmentEntry = assignment.get(atomOf(integer));
			int atomDecisionLevel = assignmentEntry.getWeakDecisionLevel();
			if (assignment instanceof TrailAssignment) {
				atomDecisionLevel = Math.min(atomDecisionLevel, ((TrailAssignment) assignment).getOutOfOrderDecisionLevel(atomOf(integer)));
			}
			if (atomDecisionLevel == highestDecisionLevel) {
				numLiteralsOfHighestDecisionLevel++;
			}
			if (atomDecisionLevel > highestDecisionLevel) {
				secondHighestDecisionLevel = highestDecisionLevel;
				highestDecisionLevel = atomDecisionLevel;
				numLiteralsOfHighestDecisionLevel = 1;
			} else {
				if (atomDecisionLevel < highestDecisionLevel && atomDecisionLevel > secondHighestDecisionLevel) {
					secondHighestDecisionLevel = atomDecisionLevel;
				}
			}
		}
		if (numLiteralsOfHighestDecisionLevel != 1) {
			LOGGER.trace("NoGood contains not just one literal in the second-highest decision level. Backjumping decision level is -1.");
			return -1;
		}
		LOGGER.trace("Backjumping decision level is: {}", secondHighestDecisionLevel);
		return secondHighestDecisionLevel;
	}
}
