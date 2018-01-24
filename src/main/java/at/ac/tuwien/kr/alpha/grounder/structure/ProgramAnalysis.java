package at.ac.tuwien.kr.alpha.grounder.structure;

import at.ac.tuwien.kr.alpha.common.*;
import at.ac.tuwien.kr.alpha.common.atoms.Atom;
import at.ac.tuwien.kr.alpha.common.atoms.BasicAtom;
import at.ac.tuwien.kr.alpha.common.atoms.FixedInterpretationLiteral;
import at.ac.tuwien.kr.alpha.common.atoms.Literal;
import at.ac.tuwien.kr.alpha.grounder.*;
import at.ac.tuwien.kr.alpha.solver.ThriceTruth;

import java.util.*;

import static at.ac.tuwien.kr.alpha.Util.oops;

/**
 * Copyright (c) 2017, the Alpha Team.
 */
public class ProgramAnalysis {

	private final Map<Predicate, HashSet<NonGroundRule>> predicateDefiningRules;
	private final PredicateDependencyGraph predicateDependencyGraph;
	private final AtomStore atomStore;
	private final WorkingMemory workingMemory;
	private final Map<Predicate, LinkedHashSet<Instance>> factsFromProgram;

	public ProgramAnalysis(Program program, AtomStore atomStore, WorkingMemory workingMemory, Map<Predicate, LinkedHashSet<Instance>> factsFromProgram) {
		this.atomStore = atomStore;
		this.workingMemory = workingMemory;
		this.factsFromProgram = factsFromProgram;
		predicateDefiningRules = new HashMap<>();
		predicateDependencyGraph = PredicateDependencyGraph.buildFromProgram(program);
	}

	public void recordDefiningRule(Predicate headPredicate, NonGroundRule rule) {
		predicateDefiningRules.putIfAbsent(headPredicate, new HashSet<>());
		predicateDefiningRules.get(headPredicate).add(rule);
	}

	public Map<Predicate, HashSet<NonGroundRule>> getPredicateDefiningRules() {
		return Collections.unmodifiableMap(predicateDefiningRules);
	}

	public static class AtomSet {
		final Atom literal;
		final Set<Substitution> complementSubstitutions;

		AtomSet(Atom literal, Set<Substitution> complementSubstitutions) {
			this.literal = literal;
			this.complementSubstitutions = complementSubstitutions;
		}

		/**
		 * Returns true if the left {@link AtomSet} is a specialization of the right {@link AtomSet}.
		 * @param left
		 * @param right
		 * @return
		 */
		static boolean isSpecialization(AtomSet left, AtomSet right) {
			if (Unification.unifyRightAtom(left.literal, right.literal) == null) {
				return false;
			}
			rightLoop:
			for (Substitution rightComplementSubstitution : right.complementSubstitutions) {
				Atom rightSubstitution = right.literal.substitute(rightComplementSubstitution).renameVariables("_X");
				for (Substitution leftComplementSubstitution : left.complementSubstitutions) {
					Atom leftSubstitution = left.literal.substitute(leftComplementSubstitution).renameVariables("_Y");
					Substitution specializingSubstitution = Unification.isMoreGeneral(rightSubstitution, leftSubstitution);
					if (specializingSubstitution != null) {
						continue rightLoop;
					}
				}
				// Right substitution has no matching left one
				return false;
			}
			return true;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder("(" + literal + ",{");
			for (Substitution complementSubstitution : complementSubstitutions) {
				sb.append(literal.substitute(complementSubstitution));
				sb.append(", ");
			}
			sb.append("})");
			return sb.toString();
		}
	}

	private Map<Predicate, List<Atom>> assignedAtoms;

	public Set<Literal> reasonsForUnjustified(int atomToJustify, Assignment currentAssignment) {
		Atom literal = atomStore.get(atomToJustify);
		return reasonsForUnjustified(literal, currentAssignment);
	}

	Set<Literal> reasonsForUnjustified(Atom atom, Assignment currentAssignment) {
		assignedAtoms = new LinkedHashMap<>();
		for (int i = 1; i <= atomStore.getHighestAtomId(); i++) {
			Assignment.Entry entry = currentAssignment.get(i);
			if (entry == null) {
				continue;
			}
			Atom assignedAtom = atomStore.get(i);
			assignedAtoms.putIfAbsent(assignedAtom.getPredicate(), new ArrayList<>());
			assignedAtoms.get(assignedAtom.getPredicate()).add(assignedAtom);
		}
		return whyNotMore(new AtomSet(atom, new LinkedHashSet<>()), new LinkedHashSet<>(), currentAssignment);
	}

	private int renamingCounter;

	private Set<Literal> whyNotMore(AtomSet toJustify, Set<AtomSet> inJustificationHigherUp, Assignment currentAssignment) {
		Set<Literal> reasons = new HashSet<>();

		Predicate predicate = toJustify.literal.getPredicate();
		// Check if literal is built-in with a fixed interpretation.
		if (toJustify.literal instanceof FixedInterpretationLiteral) {
			return reasons;
		}
		// TODO: remove fact check.
		// Check if literal is a fact.
		LinkedHashSet<Instance> factInstances = factsFromProgram.get(predicate);
		if (factInstances != null) {
			// If literal is ground, simply check containment.
			if (toJustify.literal.isGround() && factInstances.contains(new Instance(toJustify.literal.getTerms()))) {
				// Facts have no reasons, they are always justified.
				return reasons;
			}
			// Literal is non-ground, search for matching instances.
			for (Instance instance : factInstances) {
				if (Substitution.unify(toJustify.literal, instance, new Substitution()) != null) {
					// TODO: ensure that fact is not excluded by complementSubstitutions!
					return reasons;
				}
			}
		}
		HashSet<NonGroundRule> rulesDefiningPredicate = getPredicateDefiningRules().get(predicate);
		if (rulesDefiningPredicate == null) {
			// Literal is no fact and has no defining rule.
			return reasons;
		}
		eachRule:
		for (NonGroundRule nonGroundRule : rulesDefiningPredicate) {
			// First rename all variables in the rule.
			Rule rule = nonGroundRule.getRule().renameVariables("_" + renamingCounter++);
			List<Literal> body = rule.getBody();
			if (!rule.getHead().isNormal()) {
				throw oops("NonGroundRule has no normal head.");
			}
			// Unify rule head with literal to justify.
			Atom headAtom = ((DisjunctiveHead) rule.getHead()).disjunctiveAtoms.get(0);
			Substitution unifier = Unification.unifyAtoms(toJustify.literal, headAtom);
			// Skip if unification failed.
			if (unifier == null) {
				continue;
			}
			// a) Check if unifier is more precise than some substitution in the complementSubstitutions.
			for (Substitution complement : toJustify.complementSubstitutions) {
				if (Substitution.isMorePrecise(unifier, complement)) {
					continue eachRule;
				}
			}
			// b) Check if this is already justified higher up.
			if (!inJustificationHigherUp.isEmpty()) {
				// Iterate over rule body and check each literal if it is a specialization of some covered by inJustificationHigherUp.
				for (Literal bodyLiteral : body) {
					final AtomSet bodyAtomSet = new AtomSet(bodyLiteral.substitute(unifier), new LinkedHashSet<>());
					for (AtomSet higherAtomSet : inJustificationHigherUp) {
						if (AtomSet.isSpecialization(bodyAtomSet, higherAtomSet)) {
							continue eachRule;
						}
					}
				}
			}

			// c)
			// Find a negated literal of the rule that is true (extend the unifier if needed).
			Collection<Substitution> matchingSubstitutions = new ArrayList<>();
			for (Literal literal : body) {
				if (!literal.isNegated()) {
					continue;
				}
				Atom bodyAtom = literal.substitute(unifier);
				// Find more substitutions, consider currentAssignment.
				// TODO: consider fact instances here, too!!
				List<Atom> assignedAtomsOverPredicate = assignedAtoms.get(bodyAtom.getPredicate());
				if (assignedAtomsOverPredicate == null) {
					continue;
				}
				for (Atom assignedAtom : assignedAtomsOverPredicate) {
					if (!currentAssignment.get(atomStore.getAtomId(assignedAtom)).getTruth().toBoolean()) {
						// Atom is not assigned true/must-be-true, skip it.
						continue;
					}
					Substitution unified = Substitution.unify(bodyAtom, new Instance(assignedAtom.getTerms()), new Substitution(unifier));
					// Skip instance if it does not unify with the bodyAtom.
					if (unified != null) {
						matchingSubstitutions.add(unified);
						// Record as reason (ground body literal is negated but stored as true).
						reasons.add((Literal) bodyAtom.substitute(unified));
					}
				}
					/*
					// Find more substitutions, consider current workingMemory.
					Collection<Instance> potentiallyMatchingInstances = positiveStorage.getInstancesFromPartiallyGroundAtom(bodyAtom);

					for (Instance instance : potentiallyMatchingInstances) {
						Substitution unified = Substitution.unify(bodyAtom, instance, new Substitution(unifier));
						// Skip instance if it does not unify with the bodyAtom.
						if (unified != null) {
							matchingSubstitutions.add(unified);
							// Record as reason (ground body literal is negated but stored as true).
							reasons.add((Literal) bodyAtom.substitute(unified));
						}
					}
					*/
			}

			// d)
			Set<AtomSet> newHigherJustifications = new LinkedHashSet<>(inJustificationHigherUp);
			newHigherJustifications.add(new AtomSet(toJustify.literal.substitute(unifier), toJustify.complementSubstitutions));
			Set<Substitution> newComplementSubstitutions = new LinkedHashSet<>(toJustify.complementSubstitutions);
			newComplementSubstitutions.addAll(matchingSubstitutions);
			List<Literal> positiveBody = new ArrayList<>(body.size());
			for (Literal literal : body) {
				if (!literal.isNegated()) {
					positiveBody.add(literal);
				}
			}
			reasons.addAll(explainPosBody(positiveBody, Collections.singleton(unifier), newHigherJustifications, newComplementSubstitutions, currentAssignment));
		}

		return reasons;
	}

	private Collection<Literal> explainPosBody(List<Literal> bodyLiterals, Set<Substitution> unifiers, Set<AtomSet> inJustificationHigherUp, Set<Substitution> complementSubstitutions, Assignment currentAssignment) {
		ArrayList<Literal> reasons = new ArrayList<>();
		if (bodyLiterals.isEmpty()) {
			return reasons;
		}
		int pickedBodyLiteral = 0;
		Literal bodyLiteral = bodyLiterals.get(pickedBodyLiteral);
		for (Substitution unifier : unifiers) {
			Atom substitutedBodyLiteral = bodyLiteral.substitute(unifier);
			Set<Substitution> justifiedInstantiationsOfBodyLiteral = new LinkedHashSet<>();
			// Consider FixedInterpretationLiterals here and evaluate them.
			if (substitutedBodyLiteral instanceof FixedInterpretationLiteral && substitutedBodyLiteral.isGround()) {
				List<Substitution> substitutions = ((FixedInterpretationLiteral) substitutedBodyLiteral).getSubstitutions(new Substitution(unifier));
				justifiedInstantiationsOfBodyLiteral.addAll(substitutions);
			}
			// FIXME: might be better to use the instances from the assignment (and facts).
			// TODO: consider fact instances here, too!
			IndexedInstanceStorage storage = workingMemory.get(bodyLiteral, true);
			Collection<Instance> matchingInstances = storage.getInstancesMatching(substitutedBodyLiteral);
			for (Instance matchingInstance : matchingInstances) {
				// Check if matchingInstance of bodyLiteral is justified.
				int matchingInstanceAtomId = atomStore.add(new BasicAtom(bodyLiteral.getPredicate(), matchingInstance.terms));
				// Atom may be fact hence not occur in the Assignment.
				LinkedHashSet<Instance> factInstances = factsFromProgram.get(bodyLiteral.getPredicate());
				if (factInstances != null && factInstances.contains(matchingInstance) ||
					currentAssignment.getTruth(matchingInstanceAtomId) == ThriceTruth.TRUE) {
					// Construct corresponding substitution and add it.
					justifiedInstantiationsOfBodyLiteral.add(Substitution.unify(bodyLiteral, matchingInstance, new Substitution(unifier)));
				}
			}
			// Search justification for unjustified instances.
			Set<Substitution> newComplementSubstitution = new LinkedHashSet<>(complementSubstitutions);
			newComplementSubstitution.addAll(justifiedInstantiationsOfBodyLiteral);
			reasons.addAll(whyNotMore(
				new AtomSet(substitutedBodyLiteral, newComplementSubstitution),
				inJustificationHigherUp,
				currentAssignment));

			// Search justification for this rule being unjustified in other body literals.
			bodyLiterals.remove(bodyLiteral);
			reasons.addAll(
				explainPosBody(bodyLiterals, justifiedInstantiationsOfBodyLiteral,
					inJustificationHigherUp, complementSubstitutions, currentAssignment));
		}
		return reasons;
	}
}
