/**
 * Copyright (c) 2016-2018, the Alpha Team.
 * All rights reserved.
 * 
 * Additional changes made by Siemens.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1) Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 
 * 2) Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
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
package at.ac.tuwien.kr.alpha.grounder;

import at.ac.tuwien.kr.alpha.common.AnswerSet;
import at.ac.tuwien.kr.alpha.common.Assignment;
import at.ac.tuwien.kr.alpha.common.AtomTranslator;
import at.ac.tuwien.kr.alpha.common.NoGood;
import at.ac.tuwien.kr.alpha.common.heuristics.DomainSpecificHeuristicValues;
import at.ac.tuwien.kr.alpha.common.heuristics.HeuristicDirectiveValues;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Grounder extends AtomTranslator {
	/**
	 * Translates an answer-set represented by true atom IDs into its logical representation.
	 * 
	 * @param trueAtoms
	 * @return
	 */
	AnswerSet assignmentToAnswerSet(Iterable<Integer> trueAtoms);

	/**
	 * Applies lazy grounding and returns all newly derived (fully ground) NoGoods.
	 * 
	 * @return a mapping of nogood IDs to NoGoods.
	 */
	Map<Integer, NoGood> getNoGoods(Assignment assignment);

	/**
	 * Return choice points and their enablers and disablers.
	 * Must be preceeded by a call to getNoGoods().
	 * 
	 * @return a pair (choiceOn, choiceOff) of two maps from atomIds to atomIds,
	 *         choiceOn maps atoms (choice points) to their enabling atoms
	 *         and choiceOff maps atoms (choice points) to their disabling atoms.
	 */
	Pair<Map<Integer, Integer>, Map<Integer, Integer>> getChoiceAtoms();

	/**
	 * TODO: docs
	 * 
	 * @return
	 */
	Pair<Map<Integer, Integer>, Map<Integer, Integer>> getHeuristicAtoms();

	/**
	 * TODO: docs
	 * 
	 * @return
	 */
	Map<Integer, HeuristicDirectiveValues> getHeuristicValues();

	/**
	 * TODO: docs
	 * 
	 * @return
	 */
	Map<Integer, Set<Integer>> getHeadsToBodies();

	/**
	 * Gets and resets the set of newly grounded information on domain-specific heuristic
	 * 
	 * @return a mapping from rule atom IDs to domain-specific heuristic values defined for the corresponding ground rule
	 * 
	 * @deprecated Use {@link #getHeuristicValues()} instead
	 */
	@Deprecated
	Map<Integer, DomainSpecificHeuristicValues> getDomainChoiceHeuristics();

	void updateAssignment(Iterator<Assignment.Entry> it);

	void forgetAssignment(int[] atomIds);

	/**
	 * Returns a list of currently known but unassigned.
	 * 
	 * @param assignment
	 *          the current assignment.
	 * @return a list of atoms not having assigned a truth value.
	 */
	List<Integer> getUnassignedAtoms(Assignment assignment);

	/**
	 * Registers the given NoGood and returns the identifier of it.
	 * 
	 * @param noGood
	 * @return
	 */
	int register(NoGood noGood);

	/**
	 * Returns true whenever the atom is a valid choice point (i.e., it represents a rule body).
	 * 
	 * @param atom
	 * @return
	 */
	boolean isAtomChoicePoint(int atom);

	/**
	 * Returns the highest atomId in use.
	 * 
	 * @return the highest atomId in use.
	 */
	int getMaxAtomId();

	AtomStore getAtomStore();
}
