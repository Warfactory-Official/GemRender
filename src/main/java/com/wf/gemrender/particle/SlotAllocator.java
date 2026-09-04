package com.wf.gemrender.particle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SlotAllocator {
	private final List<int[]> free = new ArrayList<>();

	private int capacity;

	public SlotAllocator(int capacity) {
		this.capacity = capacity;
		free.add(new int[] { 0, capacity });
	}

	public int capacity() {
		return capacity;
	}

	public int allocate(int slots) {
		if (slots <= 0) {
			throw new IllegalArgumentException("A pool needs at least one slot");
		}

		int base = take(slots);
		if (base >= 0) {
			return base;
		}

		int target = Math.max(capacity * 2, capacity + slots);
		free.add(new int[] { capacity, target - capacity });
		capacity = target;
		coalesce();

		return take(slots);
	}

	public void release(int base, int slots) {
		if (slots <= 0) {
			return;
		}

		free.add(new int[] { base, slots });
		coalesce();
	}

	public int freeSlots() {
		int total = 0;
		for (int[] range : free) {
			total += range[1];
		}
		return total;
	}

	public int freeRanges() {
		return free.size();
	}

	private int take(int slots) {
		for (int i = 0; i < free.size(); i++) {
			int[] range = free.get(i);
			if (range[1] < slots) {
				continue;
			}

			int base = range[0];
			if (range[1] == slots) {
				free.remove(i);
			} else {
				range[0] += slots;
				range[1] -= slots;
			}
			return base;
		}
		return -1;
	}

	private void coalesce() {
		free.sort(Comparator.comparingInt(range -> range[0]));

		for (int i = free.size() - 1; i > 0; i--) {
			int[] previous = free.get(i - 1);
			int[] current = free.get(i);
			if (previous[0] + previous[1] == current[0]) {
				previous[1] += current[1];
				free.remove(i);
			}
		}
	}
}
