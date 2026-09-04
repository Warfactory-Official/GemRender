package com.wf.gemrender.particle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlotAllocatorTest {
	@Test
	@DisplayName("consecutive allocations do not overlap")
	void allocationsDoNotOverlap() {
		SlotAllocator allocator = new SlotAllocator(256);

		int first = allocator.allocate(32);
		int second = allocator.allocate(64);
		int third = allocator.allocate(32);

		assertThat(first + 32).isLessThanOrEqualTo(second);
		assertThat(second + 64).isLessThanOrEqualTo(third);
		assertThat(allocator.freeSlots()).isEqualTo(256 - 128);
	}

	@Test
	@DisplayName("a released range is handed straight back out")
	void releaseIsReusable() {
		SlotAllocator allocator = new SlotAllocator(128);

		int base = allocator.allocate(64);
		allocator.release(base, 64);

		assertThat(allocator.allocate(64)).isEqualTo(base);
		assertThat(allocator.capacity()).isEqualTo(128);
	}

	@Test
	@DisplayName("adjacent released ranges coalesce into one that satisfies a larger request")
	void releaseCoalesces() {
		SlotAllocator allocator = new SlotAllocator(96);

		int first = allocator.allocate(32);
		int second = allocator.allocate(32);
		int third = allocator.allocate(32);
		assertThat(allocator.freeSlots()).isZero();

		allocator.release(first, 32);
		allocator.release(second, 32);
		allocator.release(third, 32);

		assertThat(allocator.freeRanges()).isEqualTo(1);
		assertThat(allocator.allocate(96)).isEqualTo(first);
		assertThat(allocator.capacity()).isEqualTo(96);
	}

	@Test
	@DisplayName("a request larger than what is free grows the capacity rather than failing")
	void growsWhenExhausted() {
		SlotAllocator allocator = new SlotAllocator(64);

		allocator.allocate(64);
		int base = allocator.allocate(512);

		assertThat(allocator.capacity()).isGreaterThanOrEqualTo(576);
		assertThat(base).isGreaterThanOrEqualTo(64);
	}

	@Test
	@DisplayName("a zero or negative request is rejected")
	void rejectsEmptyRequests() {
		SlotAllocator allocator = new SlotAllocator(32);

		assertThatThrownBy(() -> allocator.allocate(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> allocator.allocate(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("random allocate and release traffic never hands out an overlapping range")
	void randomTrafficStaysDisjoint() {
		SlotAllocator allocator = new SlotAllocator(64);
		Random random = new Random(20260904L);
		List<int[]> live = new ArrayList<>();

		for (int step = 0; step < 2000; step++) {
			if (live.isEmpty() || random.nextInt(100) < 60) {
				int slots = 1 + random.nextInt(48);
				int base = allocator.allocate(slots);

				for (int[] other : live) {
					assertThat(base + slots <= other[0] || other[0] + other[1] <= base).as(
									"[%d,%d) overlaps a live [%d,%d)", base, base + slots, other[0], other[0] + other[1])
							.isTrue();
				}
				assertThat(base + slots).isLessThanOrEqualTo(allocator.capacity());

				live.add(new int[] { base, slots });
			} else {
				int[] range = live.remove(random.nextInt(live.size()));
				allocator.release(range[0], range[1]);
			}
		}
	}
}
