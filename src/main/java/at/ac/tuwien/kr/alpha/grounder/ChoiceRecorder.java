/*
 * Copyright (c) 2017-2020, the Alpha Team.
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
package at.ac.tuwien.kr.alpha.grounder;

import at.ac.tuwien.kr.alpha.common.AtomStore;
import at.ac.tuwien.kr.alpha.common.NoGood;
import at.ac.tuwien.kr.alpha.common.NoGoodCreator;
import at.ac.tuwien.kr.alpha.common.atoms.Atom;
import at.ac.tuwien.kr.alpha.common.heuristics.HeuristicDirectiveAtom;
import at.ac.tuwien.kr.alpha.common.heuristics.HeuristicDirectiveLiteral;
import at.ac.tuwien.kr.alpha.common.heuristics.HeuristicDirectiveValues;
import at.ac.tuwien.kr.alpha.grounder.atoms.HeuristicAtom;
import at.ac.tuwien.kr.alpha.grounder.atoms.HeuristicInfluencerAtom;
import at.ac.tuwien.kr.alpha.grounder.atoms.RuleAtom;
import at.ac.tuwien.kr.alpha.solver.ThriceTruth;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static at.ac.tuwien.kr.alpha.Util.oops;
import static at.ac.tuwien.kr.alpha.common.Literals.atomToLiteral;
import static at.ac.tuwien.kr.alpha.common.Literals.negateLiteral;
import static at.ac.tuwien.kr.alpha.common.heuristics.HeuristicSignSetUtil.isF;
import static at.ac.tuwien.kr.alpha.common.heuristics.HeuristicSignSetUtil.isProcessable;
import static at.ac.tuwien.kr.alpha.grounder.atoms.ChoiceAtom.off;
import static at.ac.tuwien.kr.alpha.grounder.atoms.ChoiceAtom.on;
import static java.util.Collections.emptyList;

public class ChoiceRecorder {
	static final IntIdGenerator ID_GENERATOR = new IntIdGenerator();

	private final AtomStore atomStore;
	private Pair<Map<Integer, Integer>, Map<Integer, Integer>> newChoiceAtoms = new ImmutablePair<>(new LinkedHashMap<>(), new LinkedHashMap<>());
	private Pair<Map<Integer, Integer>, Map<Integer, Integer>> newHeuristicAtoms = new ImmutablePair<>(new LinkedHashMap<>(), new LinkedHashMap<>());
	private Map<Integer, HeuristicDirectiveValues> newHeuristicValues = new LinkedHashMap<>();
	private Map<Integer, Set<Integer>> newHeadsToBodies = new LinkedHashMap<>();

	public ChoiceRecorder(AtomStore atomStore) {
		this.atomStore = atomStore;
	}

	/**
	 * @return new choice points and their enablers and disablers.
	 */
	public Pair<Map<Integer, Integer>, Map<Integer, Integer>> getAndResetChoices() {
		Pair<Map<Integer, Integer>, Map<Integer, Integer>> currentChoiceAtoms = newChoiceAtoms;
		newChoiceAtoms = new ImmutablePair<>(new LinkedHashMap<>(), new LinkedHashMap<>());
		return currentChoiceAtoms;
	}

	/**
	 * @return a set of new mappings from head atoms to {@link RuleAtom}s deriving it.
	 */
	public Map<Integer, Set<Integer>> getAndResetHeadsToBodies() {
		Map<Integer, Set<Integer>> currentHeadsToBodies = newHeadsToBodies;
		newHeadsToBodies = new LinkedHashMap<>();
		return currentHeadsToBodies;
	}


	/**
	 * @return new heuristic atoms and their enablers and disablers.
	 */
	public Pair<Map<Integer, Integer>, Map<Integer, Integer>> getAndResetHeuristics() {
		Pair<Map<Integer, Integer>, Map<Integer, Integer>> currentHeuristicAtoms = newHeuristicAtoms;
		newHeuristicAtoms = new ImmutablePair<>(new LinkedHashMap<>(), new LinkedHashMap<>());
		return currentHeuristicAtoms;
	}

	/**
	 * @return a set of new mappings from heuristic atoms to {@link HeuristicDirectiveValues}.
	 */
	public Map<Integer, HeuristicDirectiveValues> getAndResetHeuristicValues() {
		Map<Integer, HeuristicDirectiveValues> currentHeuristicValues = newHeuristicValues;
		newHeuristicValues = new LinkedHashMap<>();
		return currentHeuristicValues;
	}


	public List<NoGood> generateChoiceNoGoods(final List<Integer> posLiterals, final List<Integer> negLiterals, final int bodyRepresentingAtom) {
		// Obtain an ID for this new choice.
		final int choiceId = ID_GENERATOR.getNextId();
		// Create ChoiceOn and ChoiceOff atoms.
		final int choiceOnAtom = atomStore.putIfAbsent(on(choiceId));
		newChoiceAtoms.getLeft().put(bodyRepresentingAtom, choiceOnAtom);
		final int choiceOffAtom = atomStore.putIfAbsent(off(choiceId));
		newChoiceAtoms.getRight().put(bodyRepresentingAtom, choiceOffAtom);

		final List<NoGood> noGoods = generateNeg(choiceOffAtom, negLiterals);
		noGoods.add(generatePos(choiceOnAtom, posLiterals));

		return noGoods;
	}

	public Collection<NoGood> generateHeuristicNoGoods(HeuristicAtom groundHeuristicAtom, final int bodyRepresentingAtom, final int headId) {
		// Obtain an ID for this new heuristic.
		final int heuristicId = ID_GENERATOR.getNextId();

		final List<NoGood> noGoods = new ArrayList<>();
		for (HeuristicDirectiveLiteral heuristicDirectiveLiteral : groundHeuristicAtom.getOriginalCondition()) {
			final HeuristicDirectiveAtom heuristicDirectiveAtom = heuristicDirectiveLiteral.getAtom();
			final Set<ThriceTruth> signSet = heuristicDirectiveAtom.getSigns();
			final int heuristicInfluencerAtom = atomStore.putIfAbsent(HeuristicInfluencerAtom.get(!heuristicDirectiveLiteral.isNegated(), heuristicId, signSet));
			noGoods.add(generateHeuristicNoGood(heuristicDirectiveAtom.getAtom(), signSet, heuristicInfluencerAtom));
		}

		if (newHeuristicValues.put(bodyRepresentingAtom, HeuristicDirectiveValues.fromHeuristicAtom(groundHeuristicAtom, headId)) != null) {
			throw oops("Same heuristic body-representing atom used for two heuristic directives");
		}

		return noGoods;
	}

	private NoGood generateHeuristicNoGood(Atom atom, Set<ThriceTruth> signSet, int heuristicInfluencerAtom) {
		if (!isProcessable(signSet)) {
			throw oops("Heuristic sign not processable: " + signSet);
		}
		final int atomID = atomStore.putIfAbsent(atom);
		return NoGoodCreator.headFirstInternal(atomToLiteral(heuristicInfluencerAtom, false), atomToLiteral(atomID, !isF(signSet)));
	}

	private NoGood generatePos(final int atomOn, List<Integer> posLiterals) {
		final int literalOn = atomToLiteral(atomOn);

		return NoGoodCreator.fromBodyInternal(posLiterals, emptyList(), literalOn);
	}

	private List<NoGood> generateNeg(final int atomOff, List<Integer> negLiterals)  {
		final int negLiteralOff = negateLiteral(atomToLiteral(atomOff));

		final List<NoGood> noGoods = new ArrayList<>(negLiterals.size() + 1);
		for (Integer negLiteral : negLiterals) {
			// Choice is off if any of the negative atoms is assigned true,
			// hence we add one nogood for each such atom.
			noGoods.add(NoGoodCreator.headFirstInternal(negLiteralOff, negLiteral));
		}
		return noGoods;
	}

	public void addHeadToBody(int headId, int bodyId) {
		Set<Integer> existingBodies = newHeadsToBodies.get(headId);
		if (existingBodies == null) {
			existingBodies = new HashSet<>();
			newHeadsToBodies.put(headId, existingBodies);
		}
		existingBodies.add(bodyId);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("[enablers: ");
		for (Map.Entry<Integer, Integer> enablers : newChoiceAtoms.getLeft().entrySet()) {
			sb.append(enablers.getKey()).append("/").append(enablers.getValue()).append(", ");
		}
		sb.append(" disablers: ");
		for (Map.Entry<Integer, Integer> disablers : newChoiceAtoms.getRight().entrySet()) {
			sb.append(disablers.getKey()).append("/").append(disablers.getValue());
		}
		return sb.append("]").toString();
	}
}
