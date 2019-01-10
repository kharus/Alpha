/**
 * Copyright (c) 2017-2018, the Alpha Team.
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
package at.ac.tuwien.kr.alpha;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Ignore;
import org.junit.Test;

import at.ac.tuwien.kr.alpha.common.AnswerSet;
import at.ac.tuwien.kr.alpha.common.AnswerSetBuilder;
import at.ac.tuwien.kr.alpha.common.DisjunctiveHead;
import at.ac.tuwien.kr.alpha.common.Predicate;
import at.ac.tuwien.kr.alpha.common.Program;
import at.ac.tuwien.kr.alpha.common.Rule;
import at.ac.tuwien.kr.alpha.common.atoms.BasicAtom;
import at.ac.tuwien.kr.alpha.common.atoms.ExternalAtom;
import at.ac.tuwien.kr.alpha.common.atoms.ExternalLiteral;
import at.ac.tuwien.kr.alpha.common.atoms.external.ExternalAtoms;
import at.ac.tuwien.kr.alpha.common.fixedinterpretations.MethodPredicateInterpretation;
import at.ac.tuwien.kr.alpha.common.terms.ConstantTerm;
import at.ac.tuwien.kr.alpha.config.AlphaConfig;
import at.ac.tuwien.kr.alpha.config.InputConfig;
import at.ac.tuwien.kr.alpha.config.InputConfig.InputSource;
import at.ac.tuwien.kr.alpha.grounder.parser.InlineDirectives;

public class AlphaTest {
	private static int invocations;

	@at.ac.tuwien.kr.alpha.common.atoms.external.Predicate
	public static boolean isOne(int term) {
		invocations++;
		return term == 1;
	}

	@at.ac.tuwien.kr.alpha.common.atoms.external.Predicate
	public static boolean isFoo(Integer a) {
		return a == 0xF00;
	}

	@at.ac.tuwien.kr.alpha.common.atoms.external.Predicate
	public static boolean thinger(Thingy thingy) {
		return true;
	}

	@Test
	public void withExternal() throws Exception {
		Alpha alpha = new Alpha();
		InputConfig inputCfg = InputConfig.forString("a :- &isOne[1].");
		inputCfg.addPredicateMethod("isOne", ExternalAtoms.processPredicateMethod(this.getClass().getMethod("isOne", int.class)));
		Program program = alpha.readProgram(inputCfg);
		Set<AnswerSet> actual = alpha.solve(program).collect(Collectors.toSet());
		Set<AnswerSet> expected = new HashSet<>(singletonList(new AnswerSetBuilder().predicate("a").build()));
		assertEquals(expected, actual);
	}

	@Test
	public void addsFacts() throws Exception {
		Alpha system = new Alpha();
		Program program = new Program();
		Thingy a = new Thingy();
		Thingy b = new Thingy();
		List<Thingy> things = asList(a, b);
		program.withExternalFacts(things);
		Set<AnswerSet> actual = system.solve(program).collect(Collectors.toSet());
		Set<AnswerSet> expected = new HashSet<>(singletonList(new AnswerSetBuilder().predicate("thingy").instance(a).instance(b).build()));
		assertEquals(expected, actual);
	}

	@Test(expected = IllegalArgumentException.class)
	public void withExternalTypeConflict() throws Exception {
		Alpha system = new Alpha();
		InputConfig inputCfg = InputConfig.forString("a :- &isFoo[\"adsfnfdsf\"].");
		inputCfg.addPredicateMethod("isFoo", ExternalAtoms.processPredicateMethod(this.getClass().getMethod("isFoo", Integer.class)));
		Set<AnswerSet> actual = system.solve(system.readProgram(inputCfg)).collect(Collectors.toSet());
		Set<AnswerSet> expected = new HashSet<>(singletonList(new AnswerSetBuilder().predicate("a").build()));
		assertEquals(expected, actual);
	}

	@Test
	public void smallGraph() throws Exception {
		Alpha system = new Alpha();
		InputConfig inputCfg = InputConfig.forString("node(1). node(2). node(3). a :- &connected[1,2].");
		inputCfg.addPredicateMethod("connected", ExternalAtoms.processPredicate((Integer a, Integer b) -> (a == 1 && b == 2) || (b == 2 || b == 3)));
		Program program = system.readProgram(inputCfg);

		Set<AnswerSet> actual = system.solve(program).collect(Collectors.toSet());
		Set<AnswerSet> expected = AnswerSetsParser.parse("{ a, node(1), node(2), node(3) }");
		assertEquals(expected, actual);
	}

	@Test
	public void filterOutput() throws Exception {
		Alpha system = new Alpha();
		InputConfig inputCfg = InputConfig.forString("node(1). node(2). outgoing13(X) :- node(X), &getLargeGraphEdges(13,X).");
		inputCfg.addPredicateMethod("getLargeGraphEdges",
				ExternalAtoms.processPredicate(() -> new HashSet<>(asList(asList(ConstantTerm.getInstance(1), ConstantTerm.getInstance(2)),
						asList(ConstantTerm.getInstance(2), ConstantTerm.getInstance(1)), asList(ConstantTerm.getInstance(13), ConstantTerm.getInstance(1))))));
		Program program = system.readProgram(inputCfg);
		Set<AnswerSet> actual = system.solve(program).collect(Collectors.toSet());
		Set<AnswerSet> expected = AnswerSetsParser.parse("{ node(1), node(2), outgoing13(1) }");
		assertEquals(expected, actual);
	}

	@Test
	public void supplier() throws Exception {
		Alpha system = new Alpha();
		InputConfig cfg = InputConfig.forString("node(1). a :- &bestNode(X), node(X).");
		cfg.addPredicateMethod("bestNode", ExternalAtoms.processPredicate(() -> singleton(singletonList(ConstantTerm.getInstance(1)))));
		Program prog = system.readProgram(cfg);

		Set<AnswerSet> expected = AnswerSetsParser.parse("{ node(1), a }");
		Set<AnswerSet> actual = system.solve(prog).collect(Collectors.toSet());
		assertEquals(expected, actual);
	}

	@at.ac.tuwien.kr.alpha.common.atoms.external.Predicate
	public static Set<List<ConstantTerm<?>>> bestNode() {
		return singleton(singletonList(ConstantTerm.getInstance(1)));
	}

	@Test
	public void noInput() throws Exception {
		Alpha system = new Alpha();
		InputConfig cfg = InputConfig.forString("node(1). a :- &bestNode(X), node(X).");
		cfg.addPredicateMethod("bestNode", ExternalAtoms.processPredicateMethod(this.getClass().getMethod("bestNode")));
		Program prog = system.readProgram(cfg);

		Set<AnswerSet> expected = AnswerSetsParser.parse("{ node(1), a }");
		Set<AnswerSet> actual = system.solve(prog).collect(Collectors.toSet());
		assertEquals(expected, actual);
	}

	@Test(expected = IllegalArgumentException.class)
	public void smallGraphWithWrongType() throws Exception {
		Alpha system = new Alpha();
		InputConfig cfg = InputConfig.forString("a :- &connected[\"hello\",2].");
		cfg.addPredicateMethod("connected", ExternalAtoms.processPredicate((Integer a, Integer b) -> (a == 1 && b == 2) || (b == 2 || b == 3)));
		Program prog = system.readProgram(cfg);

		system.solve(prog).collect(Collectors.toSet());
	}

	public static Set<List<ConstantTerm<Integer>>> neighbors(int node) {
		if (node == 1) {
			return new HashSet<>(asList(singletonList(ConstantTerm.getInstance(2)), singletonList(ConstantTerm.getInstance(3))));
		}
		return emptySet();
	}

	public static Set<List<ConstantTerm<Integer>>> coolNode(int node) {
		if (node == 1) {
			return singleton(emptyList());
		}
		return emptySet();
	}

	@Test
	@Ignore("Test program is not safe (external lacking output variables). This should throw some exception.")
	public void smallGraphNoNeighbors() throws Exception {
		Alpha system = new Alpha();
		InputConfig cfg = InputConfig.forString("noNeighbors(2) :- not &neighbors[2].");
		cfg.addPredicateMethod("neighbors", ExternalAtoms.processPredicateMethod(this.getClass().getMethod("neighbors", int.class)));
		Program prog = system.readProgram(cfg);

		Set<AnswerSet> expected = AnswerSetsParser.parse("{ noNeighbors(2) }");
		Set<AnswerSet> actual = system.solve(prog).collect(Collectors.toSet());
		assertEquals(expected, actual);
	}

	@Test
	public void smallGraphCoolNode() throws Exception {
		Alpha system = new Alpha();
		InputConfig cfg = InputConfig.forString("node(1..2). in(X) :- node(X), &coolNode[X].");
		cfg.addPredicateMethod("coolNode", ExternalAtoms.processPredicateMethod(this.getClass().getMethod("coolNode", int.class)));
		Program prog = system.readProgram(cfg);

		Set<AnswerSet> actual = system.solve(prog).collect(Collectors.toSet());
		Set<AnswerSet> expected = AnswerSetsParser.parse("{ in(1), node(1), node(2) }");
		assertEquals(expected, actual);
	}

	@Test
	public void smallGraphSingleNeighbor() throws Exception {
		Alpha system = new Alpha();
		InputConfig cfg = InputConfig.forString("node(1..3). in(1,X) :- &neighbors[1](X), node(X).");
		cfg.addPredicateMethod("neighbors", ExternalAtoms.processPredicateMethod(this.getClass().getMethod("neighbors", int.class)));
		Program prog = system.readProgram(cfg);

		Set<AnswerSet> expected = AnswerSetsParser.parse("{ in(1,2), in(1,3), node(1), node(2), node(3) }");
		Set<AnswerSet> actual = system.solve(prog).collect(Collectors.toSet());
		assertEquals(expected, actual);
	}

	@Test
	@Ignore("Test program is not safe (external lacking output variables). This should throw some exception.")
	public void smallGraphSingleNeighborNoTerm() throws Exception {
		Alpha system = new Alpha();
		InputConfig cfg = InputConfig.forString("success :- &neighbors[1], not &neighbors[2].");
		cfg.addPredicateMethod("neighbors", ExternalAtoms.processPredicateMethod(this.getClass().getMethod("neighbors", int.class)));
		Program prog = system.readProgram(cfg);

		Set<AnswerSet> expected = AnswerSetsParser.parse("{ success }");
		Set<AnswerSet> actual = system.solve(prog).collect(Collectors.toSet());
		assertEquals(expected, actual);
	}

	private static class Thingy implements Comparable<Thingy> {
		@Override
		public String toString() {
			return "thingy";
		}

		@Override
		public int compareTo(Thingy o) {
			return 0;
		}
	}

	private static class SubThingy extends Thingy {
	}

	@Test
	public void withExternalSubtype() throws Exception {
		SubThingy thingy = new SubThingy();

		Rule rule = new Rule(new DisjunctiveHead(Collections.singletonList(new BasicAtom(Predicate.getInstance("p", 1), ConstantTerm.getInstance("x")))),
				singletonList(new ExternalLiteral(new ExternalAtom(Predicate.getInstance("thinger", 1),
						new MethodPredicateInterpretation(this.getClass().getMethod("thinger", Thingy.class)), singletonList(ConstantTerm.getInstance(thingy)),
						emptyList()), true)));

		Alpha system = new Alpha();

		Program prog = new Program(singletonList(rule), emptyList(), new InlineDirectives());

		Set<AnswerSet> actual = system.solve(prog).collect(Collectors.toSet());
		Set<AnswerSet> expected = new HashSet<>(singletonList(new AnswerSetBuilder().predicate("p").instance("x").build()));
		assertEquals(expected, actual);
	}

	@Test
	public void withExternalViaAnnotation() throws Exception {
		Alpha system = new Alpha();
		InputConfig cfg = InputConfig.forString("a :- &isOne[1].");
		cfg.setPredicateMethods(ExternalAtoms.scan(this.getClass().getPackage().getName()));
		Program prog = system.readProgram(cfg);

		Set<AnswerSet> actual = system.solve(prog).collect(Collectors.toSet());
		Set<AnswerSet> expected = new HashSet<>(singletonList(new AnswerSetBuilder().predicate("a").build()));
		assertEquals(expected, actual);
	}

	@Test
	public void withNativeExternal() throws Exception {
		Alpha system = new Alpha();
		InputConfig cfg = InputConfig.forString("a :- &isTwo[2].");
		cfg.addPredicateMethod("isTwo", ExternalAtoms.processPredicate((Integer t) -> t == 2));
		Program prog = system.readProgram(cfg);

		Set<AnswerSet> actual = system.solve(prog).collect(Collectors.toSet());
		Set<AnswerSet> expected = new HashSet<>(singletonList(new AnswerSetBuilder().predicate("a").build()));
		assertEquals(expected, actual);
	}

	@Test
	@Ignore("External atom has state, which is not allowed. Caching of calls makes the number of invocations wrong.")
	public void withExternalInvocationCounted1() throws Exception {
		Alpha system = new Alpha();
		InputConfig cfg = InputConfig.forString("a :- &isOne[1], &isOne[1].");
		cfg.addPredicateMethod("isOne", ExternalAtoms.processPredicateMethod(this.getClass().getMethod("isOne", int.class)));
		Program prog = system.readProgram(cfg);

		int before = invocations;
		Set<AnswerSet> actual = system.solve(prog).collect(Collectors.toSet());
		int after = invocations;

		assertEquals(2, after - before);

		Set<AnswerSet> expected = new HashSet<>(singletonList(new AnswerSetBuilder().predicate("a").build()));
		assertEquals(expected, actual);
	}

	@Test
	@Ignore("External atom has state, which is not allowed. Caching of calls makes the number of invocations wrong.")
	public void withExternalInvocationCounted2() throws Exception {
		Alpha system = new Alpha();
		InputConfig cfg = InputConfig.forString("a. b :- &isOne[1], &isOne[2].");
		cfg.addPredicateMethod("isOne", ExternalAtoms.processPredicateMethod(this.getClass().getMethod("isOne", int.class)));
		Program prog = system.readProgram(cfg);

		int before = invocations;
		Set<AnswerSet> actual = system.solve(prog).collect(Collectors.toSet());
		int after = invocations;

		assertEquals(2, after - before);

		Set<AnswerSet> expected = new HashSet<>(singletonList(new AnswerSetBuilder().predicate("a").build()));
		assertEquals(expected, actual);
	}

	@Test
	@Ignore("External atom has state, which is not allowed. Caching of calls makes the number of invocations wrong.")
	public void withExternalInvocationCounted3() throws Exception {
		Alpha system = new Alpha();
		InputConfig cfg = InputConfig.forString("a :- &isOne[1], not &isOne[2].");
		cfg.addPredicateMethod("isOne", ExternalAtoms.processPredicateMethod(this.getClass().getMethod("isOne", int.class)));
		Program prog = system.readProgram(cfg);

		int before = invocations;
		Set<AnswerSet> actual = system.solve(prog).collect(Collectors.toSet());
		int after = invocations;

		assertEquals(1, after - before);

		Set<AnswerSet> expected = new HashSet<>(singletonList(new AnswerSetBuilder().predicate("a").build()));
		assertEquals(expected, actual);
	}

	@Test
	public void basicUsage() throws Exception {
		Alpha system = new Alpha();
		Set<AnswerSet> actual = system.solve(system.readProgram(InputConfig.forString("p(a)."))).collect(Collectors.toSet());
		Set<AnswerSet> expected = new HashSet<>(singletonList(new AnswerSetBuilder().predicate("p").symbolicInstance("a").build()));
		assertEquals(expected, actual);
	}

	@Test
	public void basicUsageWithString() throws Exception {
		Alpha system = new Alpha();
		Set<AnswerSet> actual = system.solve(system.readProgram(InputConfig.forString("p(\"a\")."))).collect(Collectors.toSet());
		Set<AnswerSet> expected = new HashSet<>(singletonList(new AnswerSetBuilder().predicate("p").instance("a").build()));
		assertEquals(expected, actual);
	}

	/**
	 * Runs a test that formerly caused some sort of exception.
	 */
	@Test
	public void problematicRun_3col_1119654162577372() throws IOException {
		/*
		 * NOTE: This was constructed from the following commandline invocation:
		 * -DebugEnableInternalChecks -q -g naive -s default -e 1119654162577372 -n 200
		 * -i 3col-20-38.txt
		 */
		problematicRun("3col-20-38.txt", 1119654162577372L, 200);
	}

	/**
	 * Runs a test that formerly caused some sort of exception.
	 */
	@Test
	public void problematicRun_3col_1119718541727902() throws IOException {
		/*
		 * NOTE: This was constructed from the following commandline invocation:
		 * -DebugEnableInternalChecks -q -g naive -s default -e 1119718541727902 -n 200
		 * -i 3col-20-38.txt
		 */
		problematicRun("3col-20-38.txt", 1119718541727902L, 200);
	}

	/**
	 * Runs a test that formerly caused some sort of exception.
	 */
	@Test
	public void problematicRun_vehicle_97598271567626() throws IOException {
		/*
		 * NOTE: This was constructed from the following commandline invocation:
		 * -DebugEnableInternalChecks -q -g naive -s default -e 97598271567626 -n 2 -i
		 * vehicle_normal_small.asp
		 */
		problematicRun("vehicle_normal_small.asp", 1119718541727902L, 2);
	}

	/**
	 * Runs a test that formerly caused some sort of exception.
	 */
	@Test
	public void problematicRun_3col_1119718541727902_sorted_400() throws IOException {
		/*
		 * NOTE: This was constructed from the following commandline invocation:
		 * -DebugEnableInternalChecks -q -g naive -s default -sort -n 400 -i
		 * 3col-20-38.txt
		 */
		AlphaConfig cfg = new AlphaConfig();
		cfg.setGrounderName("naive");
		cfg.setSolverName("default");
		cfg.setNogoodStoreName("alpharoaming");
		cfg.setDebugInternalChecks(true);
		cfg.setSeed(1119718541727902L);
		final Alpha system = new Alpha(cfg);

		final Path path = Paths.get("src", "test", "resources", "PreviouslyProblematic").resolve("3col-20-38.txt");
		InputConfig inputCfg = new InputConfig();
		inputCfg.setSource(InputSource.FILE);
		List<String> files = new ArrayList<>();
		files.add(path.toString());
		inputCfg.setFiles(files);
		Program prog = system.readProgram(inputCfg);

		assertFalse(system.solve(prog).sorted().limit(400).collect(Collectors.toList()).isEmpty());
	}

	private void problematicRun(String program, long seed, int limit) throws IOException {
		final Path base = Paths.get("src", "test", "resources", "PreviouslyProblematic");
		AlphaConfig cfg = new AlphaConfig();
		cfg.setGrounderName("naive");
		cfg.setSolverName("default");
		cfg.setNogoodStoreName("alpharoaming");
		cfg.setDebugInternalChecks(true);
		cfg.setSeed(seed);
		final Alpha system = new Alpha(cfg);
		InputConfig inputCfg = new InputConfig();
		inputCfg.setSource(InputSource.FILE);
		List<String> files = new ArrayList<>();
		files.add(base.resolve(program).toString());
		inputCfg.setFiles(files);
		Program prog = system.readProgram(inputCfg);
		assertFalse(system.solve(prog).limit(limit).collect(Collectors.toList()).isEmpty());
	}
}