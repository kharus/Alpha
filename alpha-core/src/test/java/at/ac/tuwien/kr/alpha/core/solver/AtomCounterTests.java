/**
 * Copyright (c) 2019 Siemens AG
 * All rights reserved.
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
package at.ac.tuwien.kr.alpha.core.solver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import at.ac.tuwien.kr.alpha.api.program.Atom;
import at.ac.tuwien.kr.alpha.api.rules.CompiledRule;
import at.ac.tuwien.kr.alpha.api.terms.ConstantTerm;
import at.ac.tuwien.kr.alpha.api.terms.Term;
import at.ac.tuwien.kr.alpha.commons.Terms;
import at.ac.tuwien.kr.alpha.core.atoms.AggregateAtom;
import at.ac.tuwien.kr.alpha.core.atoms.BasicAtom;
import at.ac.tuwien.kr.alpha.core.atoms.ChoiceAtom;
import at.ac.tuwien.kr.alpha.core.atoms.RuleAtom;
import at.ac.tuwien.kr.alpha.core.common.AtomStore;
import at.ac.tuwien.kr.alpha.core.common.AtomStoreImpl;
import at.ac.tuwien.kr.alpha.core.common.ComparisonOperatorImpl;
import at.ac.tuwien.kr.alpha.core.common.CorePredicate;
import at.ac.tuwien.kr.alpha.core.grounder.SubstitutionImpl;
import at.ac.tuwien.kr.alpha.core.rules.InternalRule;
import at.ac.tuwien.kr.alpha.core.rules.heads.NormalHeadImpl;

public class AtomCounterTests {

	private AtomStore atomStore;

	@Before
	public void setUp() {
		this.atomStore = new AtomStoreImpl();
	}

	@Test
	public void testGetNumberOfAtoms() throws NoSuchMethodException {
		final AtomCounter atomCounter = atomStore.getAtomCounter();

		expectGetNumberOfAtoms(atomCounter, BasicAtom.class, 0);
		expectGetNumberOfAtoms(atomCounter, AggregateAtom.class, 0);
		expectGetNumberOfAtoms(atomCounter, ChoiceAtom.class, 0);
		expectGetNumberOfAtoms(atomCounter, RuleAtom.class, 0);

		createBasicAtom1();
		createBasicAtom2();
		createAggregateAtom();
		createChoiceAtom();
		createRuleAtom();

		expectGetNumberOfAtoms(atomCounter, BasicAtom.class, 2);
		expectGetNumberOfAtoms(atomCounter, AggregateAtom.class, 1);
		expectGetNumberOfAtoms(atomCounter, ChoiceAtom.class, 1);
		expectGetNumberOfAtoms(atomCounter, RuleAtom.class, 1);
	}

	@Test
	public void testGetStatsByType() throws NoSuchMethodException {
		final AtomCounter atomCounter = atomStore.getAtomCounter();

		createBasicAtom1();
		createBasicAtom2();
		createAggregateAtom();
		createChoiceAtom();
		createRuleAtom();

		expectGetStatsByType(atomCounter, BasicAtom.class, 2);
		expectGetStatsByType(atomCounter, AggregateAtom.class, 1);
		expectGetStatsByType(atomCounter, ChoiceAtom.class, 1);
		expectGetStatsByType(atomCounter, RuleAtom.class, 1);
	}

	private void createBasicAtom1() {
		atomStore.putIfAbsent(new BasicAtom(CorePredicate.getInstance("p", 0)));
	}

	private void createBasicAtom2() {
		atomStore.putIfAbsent(new BasicAtom(CorePredicate.getInstance("q", 1), Terms.newConstant(1)));
	}

	private void createAggregateAtom() {
		final ConstantTerm<Integer> c1 = Terms.newConstant(1);
		final ConstantTerm<Integer> c2 = Terms.newConstant(2);
		final ConstantTerm<Integer> c3 = Terms.newConstant(3);
		List<Term> basicTerms = Arrays.asList(c1, c2, c3);
		AggregateAtom.AggregateElement aggregateElement = new AggregateAtom.AggregateElement(basicTerms, Collections.singletonList(new BasicAtom(CorePredicate.getInstance("p", 3), c1, c2, c3).toLiteral()));
		atomStore.putIfAbsent(new AggregateAtom(ComparisonOperatorImpl.LE, c1, null, null, AggregateAtom.AGGREGATEFUNCTION.COUNT, Collections.singletonList(aggregateElement)));
	}

	private void createChoiceAtom() {
		atomStore.putIfAbsent(ChoiceAtom.on(1));
	}

	private void createRuleAtom() {
		Atom atomAA = new BasicAtom(CorePredicate.getInstance("aa", 0));
		CompiledRule ruleAA = new InternalRule(new NormalHeadImpl(atomAA), Collections.singletonList(new BasicAtom(CorePredicate.getInstance("bb", 0)).toLiteral(false)));
		atomStore.putIfAbsent(new RuleAtom(ruleAA, new SubstitutionImpl()));
	}

	private void expectGetNumberOfAtoms(AtomCounter atomCounter, Class<? extends Atom> classOfAtoms, int expectedNumber) {
		assertEquals("Unexpected number of " + classOfAtoms.getSimpleName() + "s", expectedNumber, atomCounter.getNumberOfAtoms(classOfAtoms));
	}

	private void expectGetStatsByType(AtomCounter atomCounter, Class<? extends Atom> classOfAtoms, int expectedNumber) {
		assertTrue("Expected number of " + classOfAtoms.getSimpleName() + "s not contained in stats string", atomCounter.getStatsByType().contains(classOfAtoms.getSimpleName() + ": " + expectedNumber));
	}

}
