package at.ac.tuwien.kr.alpha.solver;

import at.ac.tuwien.kr.alpha.common.ReadableAssignment;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Copyright (c) 2016, the Alpha Team.
 */
public class FirstUIPPriorityQueueTest {
	private final BasicAssignment assignment;

	public FirstUIPPriorityQueueTest() {
		assignment = new BasicAssignment();
	}

	@Before
	public void setUp() {
		assignment.clear();
	}

	@Test
	public void nonlinearEntries() {
		assignment.guess(1, ThriceTruth.MBT);
		assignment.assign(2, ThriceTruth.TRUE);
		assignment.assign(3, ThriceTruth.FALSE);
		assignment.assign(4, ThriceTruth.MBT);

		ReadableAssignment.Entry entry1 = assignment.get(1);
		ReadableAssignment.Entry entry2 = assignment.get(2);
		ReadableAssignment.Entry entry3 = assignment.get(3);
		ReadableAssignment.Entry entry4 = assignment.get(4);
		assertTrue(entry1.getDecisionLevel() == entry2.getDecisionLevel() && entry2.getDecisionLevel() == entry3.getDecisionLevel() && entry3.getDecisionLevel() == entry4.getDecisionLevel());
		assertTrue(entry1.getPropagationLevel() < entry2.getPropagationLevel());
		assertTrue(entry2.getPropagationLevel() < entry3.getPropagationLevel());
		assertTrue(entry3.getPropagationLevel() < entry4.getPropagationLevel());

		FirstUIPPriorityQueue firstUIPPriorityQueue = new FirstUIPPriorityQueue(entry1.getDecisionLevel());
		firstUIPPriorityQueue.add(entry2);
		firstUIPPriorityQueue.add(entry4);
		firstUIPPriorityQueue.add(entry3);

		assertEquals(3, firstUIPPriorityQueue.size());
		assertEquals(entry4, firstUIPPriorityQueue.poll());
		assertEquals(entry3, firstUIPPriorityQueue.poll());
		assertEquals(entry2, firstUIPPriorityQueue.poll());
	}

	@Test
	public void ignoreDuplicates() {
		assignment.guess(1, ThriceTruth.MBT);
		assignment.assign(2, ThriceTruth.TRUE);
		assignment.assign(3, ThriceTruth.FALSE);

		ReadableAssignment.Entry entry1 = assignment.get(1);
		ReadableAssignment.Entry entry2 = assignment.get(2);
		ReadableAssignment.Entry entry3 = assignment.get(3);

		FirstUIPPriorityQueue firstUIPPriorityQueue = new FirstUIPPriorityQueue(entry1.getDecisionLevel());
		firstUIPPriorityQueue.add(entry1);
		firstUIPPriorityQueue.add(entry2);
		firstUIPPriorityQueue.add(entry2);
		firstUIPPriorityQueue.add(entry3);
		firstUIPPriorityQueue.add(entry2);
		firstUIPPriorityQueue.add(entry3);
		firstUIPPriorityQueue.add(entry2);

		assertEquals(3, firstUIPPriorityQueue.size());
		assertEquals(entry3, firstUIPPriorityQueue.poll());
		assertEquals(entry2, firstUIPPriorityQueue.poll());
		assertEquals(entry1, firstUIPPriorityQueue.poll());
		assertEquals(null, firstUIPPriorityQueue.poll());
	}

}