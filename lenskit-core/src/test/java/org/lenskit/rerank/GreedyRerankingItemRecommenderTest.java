/*
 * LensKit, an open-source toolkit for recommender systems.
 * Copyright 2014-2017 LensKit contributors (see CONTRIBUTORS.md)
 * Copyright 2010-2014 Regents of the University of Minnesota
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.lenskit.rerank;

import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.Test;
import org.lenskit.api.ItemRecommender;
import org.lenskit.api.Result;
import org.lenskit.api.ResultList;
import org.lenskit.basic.AbstractItemRecommender;
import org.lenskit.results.Results;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class GreedyRerankingItemRecommenderTest {
    private ItemRecommender preSeededItemRecommender(final ResultList results) {
        return new AbstractItemRecommender() {
            @Override
            protected ResultList recommendWithDetails(long user, int n, @Nullable LongSet candidates, @Nullable LongSet exclude) {
                assertEquals(-1, n);
                return results;
            }
        };
    }

    @Test
    public void testParametersPassedCorrectlyAndLength() {
        List<Result> results = new ArrayList<>();
        results.add(Results.create(1,1));
        results.add(Results.create(2,2));
        results.add(Results.create(3,3));
        results.add(Results.create(4,4));
        results.add(Results.create(5,5));

        final long theUserId = 101;
        final int theN = 1;
        final ResultList rl = Results.newResultList(results);

        ItemRecommender ir = preSeededItemRecommender(rl);
        GreedyRerankStrategy selector = new GreedyRerankStrategy() {
            boolean calledOnce = false;
            @Nullable
            @Override
            public Result nextItem(long userId, int n, List<? extends Result> items, List<? extends Result> candidates) {
                // this should only be called once.
                assertFalse(calledOnce);
                calledOnce = true;

                assertEquals(theUserId, userId);
                assertEquals(theN, n);
                for (int i = 0; i<5; i++) {
                    assertEquals(rl.get(i), candidates.get(i));
                }
                assertTrue(items.isEmpty());
                return candidates.get(0);
            }
        };
        GreedyRerankingItemRecommender gr = new GreedyRerankingItemRecommender(ir, selector);
        ResultList result = gr.recommendWithDetails(theUserId, theN, null, null);
        assertEquals(1, result.size());
        assertEquals(results.get(0), result.get(0));

    }

    @Test
    public void testListTrunkatesWithNullResponse() {
        List<Result> results = new ArrayList<>();
        results.add(Results.create(1,1));
        results.add(Results.create(2,2));
        results.add(Results.create(3,3));
        results.add(Results.create(4,4));
        results.add(Results.create(5,5));

        ResultList rl = Results.newResultList(results);

        ItemRecommender ir = preSeededItemRecommender(rl);
        GreedyRerankStrategy selector = new AbstractFilteringGreedyRerankStrategy() {
            @Override
            protected boolean satisfiesConstraint(long userId, int n, List<? extends Result> items, Result candidate) {
                return (candidate.getId()%2)==0;
            }
        };

        GreedyRerankingItemRecommender gr = new GreedyRerankingItemRecommender(ir, selector);
        ResultList result = gr.recommendWithDetails(0, 3, null, null);
        assertEquals(2, result.size());
        assertEquals(results.get(1), result.get(0));
        assertEquals(results.get(3), result.get(1));
    }

    @Test
    public void testCandidatesListShrinkCorrectly() {
        List<Result> results = new ArrayList<>();
        results.add(Results.create(0,0));
        results.add(Results.create(1,1));
        results.add(Results.create(2,2));
        results.add(Results.create(3,3));
        results.add(Results.create(4,4));
        results.add(Results.create(5,5));

        ResultList rl = Results.newResultList(results);

        ItemRecommender ir = preSeededItemRecommender(rl);
        GreedyRerankStrategy selector = new GreedyRerankStrategy() {
            int expectedSize = 0;
            @Nullable
            @Override
            public Result nextItem(long userId, int n, List<? extends Result> items, List<? extends Result> candidates) {
                assertEquals(expectedSize, items.size());
                expectedSize++;
                for(int i = 0; i<items.size(); i++) {
                    assertEquals(i, items.get(i).getId());
                }
                for(int i = items.size(); i < 5; i++) {
                    assertEquals(i, candidates.get(i-items.size()).getId());
                }
                return candidates.get(0);
            }
        };

        GreedyRerankingItemRecommender gr = new GreedyRerankingItemRecommender(ir, selector);
        ResultList result = gr.recommendWithDetails(0, 6, null, null);
        assertEquals(6, result.size());
        for(int i = 0; i<6; i++ ) {
            assertEquals(rl.get(i), result.get(i));
        }
    }

    @Test
    public void testResultsOrdredCorrectly() {
        List<Result> results = new ArrayList<>();
        results.add(Results.create(0,5));
        results.add(Results.create(1,1));
        results.add(Results.create(2,4));
        results.add(Results.create(3,2));
        results.add(Results.create(4,3));
        results.add(Results.create(5,6));

        ResultList rl = Results.newResultList(results);

        ItemRecommender ir = preSeededItemRecommender(rl);
        GreedyRerankStrategy selector = new AbstractScoringGreedyRerankStrategy() {
            @Override
            protected double scoreCandidate(long userId, int n, List<? extends Result> items, Result candidate) {
                return candidate.getScore();
            }
        };

        GreedyRerankingItemRecommender gr = new GreedyRerankingItemRecommender(ir, selector);
        ResultList result = gr.recommendWithDetails(0, 10, null, null);
        assertEquals(6, result.size());
        assertEquals(5, result.get(0).getId());
        assertEquals(0, result.get(1).getId());
        assertEquals(2, result.get(2).getId());
        assertEquals(4, result.get(3).getId());
        assertEquals(3, result.get(4).getId());
        assertEquals(1, result.get(5).getId());
    }

    @Test
    public void testNegativeNMeansRecommendALot() {
        List<Result> results = new ArrayList<>();
        results.add(Results.create(0,5));
        results.add(Results.create(1,1));
        results.add(Results.create(2,4));
        results.add(Results.create(3,2));
        results.add(Results.create(4,3));
        results.add(Results.create(5,6));

        ResultList rl = Results.newResultList(results);

        ItemRecommender ir = preSeededItemRecommender(rl);
        GreedyRerankStrategy selector = new AbstractScoringGreedyRerankStrategy() {
            @Override
            protected double scoreCandidate(long userId, int n, List<? extends Result> items, Result candidate) {
                return candidate.getScore();
            }
        };

        GreedyRerankingItemRecommender gr = new GreedyRerankingItemRecommender(ir, selector);
        ResultList result = gr.recommendWithDetails(0, -1, null, null);
        assertEquals(6, result.size());
        assertEquals(5, result.get(0).getId());
        assertEquals(0, result.get(1).getId());
        assertEquals(2, result.get(2).getId());
        assertEquals(4, result.get(3).getId());
        assertEquals(3, result.get(4).getId());
        assertEquals(1, result.get(5).getId());
    }



    @Test
    public void testZeroNMeansRecommendNothing() {
        List<Result> results = new ArrayList<>();
        results.add(Results.create(0,5));
        results.add(Results.create(1,1));
        results.add(Results.create(2,4));
        results.add(Results.create(3,2));
        results.add(Results.create(4,3));
        results.add(Results.create(5,6));

        ResultList rl = Results.newResultList(results);

        ItemRecommender ir = preSeededItemRecommender(rl);
        GreedyRerankStrategy selector = new AbstractScoringGreedyRerankStrategy() {
            @Override
            protected double scoreCandidate(long userId, int n, List<? extends Result> items, Result candidate) {
                return candidate.getScore();
            }
        };

        GreedyRerankingItemRecommender gr = new GreedyRerankingItemRecommender(ir, selector);
        ResultList result = gr.recommendWithDetails(0, 0, null, null);
        assertEquals(0, result.size());
    }
}
