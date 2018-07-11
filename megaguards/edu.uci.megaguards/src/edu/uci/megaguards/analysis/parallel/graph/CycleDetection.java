/*
 * Copyright (c) 2018, Regents of the University of California
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.uci.megaguards.analysis.parallel.graph;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public class CycleDetection {

    private class Graph {
        private int vertices;
        private final Map<String, HashSet<String>> edges;
        private final HashMap<String, Integer> nodes;

        @TruffleBoundary
        private Graph() {
            this.vertices = 0;
            edges = new HashMap<>();
            this.nodes = new HashMap<>();
        }

        @TruffleBoundary
        private void addEdge(String a, String b) {
            if (!nodes.containsKey(a)) {
                nodes.put(a, this.vertices++);
                this.edges.put(a, new HashSet<String>());
            }
            if (!nodes.containsKey(b)) {
                nodes.put(b, this.vertices++);
                this.edges.put(b, new HashSet<String>());
            }
            this.edges.get(a).add(b);
        }
    }

    private final Graph graph;

    public CycleDetection() {
        this.graph = new Graph();
    }

    @TruffleBoundary
    public void addEdge(String a, String b) {
        graph.addEdge(a, b);
    }

    @TruffleBoundary
    public boolean isCyclic() {
        boolean[] visited = new boolean[graph.vertices];
        Arrays.fill(visited, false);
        Set<String> recStack = new HashSet<>();
        for (String i : graph.nodes.keySet()) {
            if (recusiveDFS(graph.nodes.get(i), i, visited, recStack))
                return true;
        }
        return false;
    }

    @TruffleBoundary
    public boolean recusiveDFS(int v, String s, boolean[] visited, Set<String> recStack) {
        if (!visited[v]) {
            visited[v] = true;
            recStack.add(s);
            for (String i : graph.edges.get(s)) {
                if (!visited[graph.nodes.get(i)]) {
                    if (recusiveDFS(graph.nodes.get(i), i, visited, recStack))
                        return true;
                } else if (recStack.contains(i))
                    return true;
            }
        }
        recStack.remove(s);
        return false;
    }

    @TruffleBoundary
    public static void main(String args[]) {
        CycleDetection c = new CycleDetection();
        c.addEdge("A", "B");
        c.addEdge("B", "C");
        c.addEdge("C", "A");
        System.out.println(c.isCyclic());
    }
}
