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
package at.ac.tuwien.kr.alpha.grounder;

import at.ac.tuwien.kr.alpha.common.atoms.Literal;

import java.util.ArrayList;
import java.util.List;

/**
 * A grounding order computed by {@link RuleGroundingOrders} for a specific {@link NonGroundRule} and a specific starting literal.
 */
public class RuleGroundingOrder {

	private Literal startingLiteral;
	private List<Literal> otherLiterals;
	private int positionLastVarBound;
	private int stopBindingAtOrderPosition;
	private final boolean ground;
	
	RuleGroundingOrder(Literal startingLiteral, List<Literal> otherLiterals, int positionLastVarBound, boolean isGround) {
		super();
		this.startingLiteral = startingLiteral;
		this.otherLiterals = otherLiterals;
		this.positionLastVarBound = positionLastVarBound;
		this.stopBindingAtOrderPosition = otherLiterals.size();
		this.ground = isGround;
	}
	
	private RuleGroundingOrder(RuleGroundingOrder otherRuleGroundingOrder) {
		this(otherRuleGroundingOrder.startingLiteral, new ArrayList<>(otherRuleGroundingOrder.otherLiterals), otherRuleGroundingOrder.positionLastVarBound, otherRuleGroundingOrder.ground);
		this.stopBindingAtOrderPosition = otherRuleGroundingOrder.stopBindingAtOrderPosition;
	}

	/**
	 * @return the startingLiteral
	 */
	public Literal getStartingLiteral() {
		return startingLiteral;
	}
	
	/**
	 * Returns the literal at the given position in the grounding order,
	 * except it is already known that this literal is not able to yield new bindings.
	 * 
	 * A literal cannot yield new bindings if it has been copied to the end of the grounding order
	 * when no bindings could be found, and no bindings for other literals could be found in the meantime.
	 * 
	 * @param orderPosition zero-based index into list of literals except the starting literal
	 * @return the literal at the given position, or {@code null} if it is already known that this literal is not able to yield new bindings
	 */
	public Literal getLiteralAtOrderPosition(int orderPosition) {
		if (orderPosition >= stopBindingAtOrderPosition) {
			return null;
		}
		return otherLiterals.get(orderPosition);
	}

	/**
	 * @return the zero-based position from which on all variables are bound in list of literals except the starting literal
	 */
	public int getPositionFromWhichAllVarsAreBound() {
		return positionLastVarBound + 1;
	}

	public boolean isGround() {
		return ground;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(startingLiteral);
		sb.append(" : ");
		for (int i = 0; i < otherLiterals.size(); i++) {
			if (i == positionLastVarBound + 1) {
				sb.append("| ");
			}
			sb.append(otherLiterals.get(i));
			if (i < otherLiterals.size() - 1) {
				sb.append(", ");
			}
		}
		
		return sb.toString();
	}

	/**
	 * @param orderPosition
	 * @return
	 */
	public RuleGroundingOrder pushBack(int orderPosition) {
		if (orderPosition >= stopBindingAtOrderPosition - 1) {
			return null;
		}
		RuleGroundingOrder reorderedGroundingOrder = new RuleGroundingOrder(this);
		reorderedGroundingOrder.otherLiterals.add(otherLiterals.get(orderPosition));
		return reorderedGroundingOrder;
	}
	
	public void considerUntilCurrentEnd() {
		this.stopBindingAtOrderPosition = this.otherLiterals.size();
	}

}
