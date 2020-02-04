/*
 * Copyright (c) 2018, 2020 Siemens AG
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
package at.ac.tuwien.kr.alpha.common;

import at.ac.tuwien.kr.alpha.common.heuristics.HeuristicDirectiveAtom;
import at.ac.tuwien.kr.alpha.common.heuristics.HeuristicDirectiveBody;

import java.util.Objects;

/**
 * Represents a heuristic directive, e.g. {@code #heuristic a : b. [2@1]}
 */
public class HeuristicDirective extends Directive {
	
	private final HeuristicDirectiveAtom head;
	private final HeuristicDirectiveBody body;
	private final WeightAtLevel weightAtLevel;

	public HeuristicDirective(HeuristicDirectiveAtom head, HeuristicDirectiveBody body, WeightAtLevel weightAtLevel) {
		super();
		this.head = head;
		this.body = body;
		this.weightAtLevel = weightAtLevel;
	}

	public HeuristicDirectiveAtom getHead() {
		return head;
	}

	public HeuristicDirectiveBody getBody() {
		return body;
	}

	public WeightAtLevel getWeightAtLevel() {
		return weightAtLevel;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		HeuristicDirective that = (HeuristicDirective) o;
		return head.equals(that.head) &&
				body.equals(that.body) &&
				weightAtLevel.equals(that.weightAtLevel);
	}

	@Override
	public int hashCode() {
		return Objects.hash(head, body, weightAtLevel);
	}

	@Override
	public String toString() {
		return "#heuristic " + head + " : " + ". [" + weightAtLevel + "]";
	}
	
}
