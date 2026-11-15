package com.sprintflowinsight.planning.optimizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/** Reproducible random backlogs for property tests and the RQ3 benchmark. */
final class RandomBacklogs {
  private RandomBacklogs() {}

  /** Shape of the generated instance. */
  record Shape(int stories, int members, boolean forest, boolean locks, boolean doneStories) {
    /** Single capacity, precedence forest: inside the dynamic program's limits. */
    static Shape singleCapacity(int stories) {
      return new Shape(stories, 0, true, true, true);
    }

    /** Per-member capacities and arbitrary acyclic dependencies. */
    static Shape general(int stories, int members) {
      return new Shape(stories, members, false, true, true);
    }
  }

  static PlanningProblem generate(long seed, Shape shape) {
    var random = new Random(seed);
    int n = shape.stories();
    List<String> members = new ArrayList<>();
    for (int m = 0; m < shape.members(); m++) {
      members.add("m" + m);
    }
    Set<String> done = new HashSet<>();
    if (shape.doneStories()) {
      done.add("done-1");
    }
    List<CandidateStory> stories = new ArrayList<>();
    long totalPoints = 0;
    Map<String, Integer> memberEffort = new TreeMap<>();
    for (int i = 0; i < n; i++) {
      String id = "s" + i;
      int points = fibonacci(random);
      long value = random.nextInt(10) == 0 ? 0 : 1 + random.nextInt(100);
      Map<String, Integer> effort = new LinkedHashMap<>();
      if (!members.isEmpty()) {
        String owner = members.get(random.nextInt(members.size()));
        effort.put(owner, points);
        memberEffort.merge(owner, points, Integer::sum);
        if (random.nextInt(4) == 0) {
          String helper = members.get(random.nextInt(members.size()));
          effort.merge(helper, 1 + random.nextInt(3), Integer::sum);
          memberEffort.merge(helper, effort.get(helper), Integer::sum);
        }
      }
      List<String> predecessors = new ArrayList<>();
      if (i > 0 && random.nextInt(3) == 0) {
        predecessors.add("s" + random.nextInt(i));
        if (!shape.forest() && random.nextInt(3) == 0) {
          predecessors.add("s" + random.nextInt(i));
        }
      }
      if (shape.doneStories() && random.nextInt(8) == 0) {
        predecessors.add("done-1");
      }
      if (random.nextInt(25) == 0) {
        predecessors.add("unknown-" + i);
      }
      Lock lock = Lock.NONE;
      if (shape.locks()) {
        int draw = random.nextInt(20);
        lock = draw == 0 ? Lock.LOCKED_IN : draw == 1 ? Lock.LOCKED_OUT : Lock.NONE;
      }
      totalPoints += points;
      stories.add(new CandidateStory(id, value, points, effort, predecessors, lock));
    }
    int total = (int) Math.max(1, totalPoints * (30 + random.nextInt(40)) / 100);
    Map<String, Integer> capacities = new TreeMap<>();
    for (String member : members) {
      int load = memberEffort.getOrDefault(member, 0);
      capacities.put(member, Math.max(1, load * (30 + random.nextInt(60)) / 100));
    }
    return new PlanningProblem(stories, done, total, capacities);
  }

  private static int fibonacci(Random random) {
    int[] scale = {0, 1, 2, 3, 5, 8, 13};
    return scale[random.nextInt(scale.length)];
  }
}
