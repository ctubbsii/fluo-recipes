/*
 * Copyright 2015 Fluo authors (see AUTHORS)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.fluo.recipes.common;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableSet;
import io.fluo.api.config.FluoConfiguration;
import io.fluo.api.data.Bytes;
import io.fluo.recipes.export.ExportQueue;
import io.fluo.recipes.map.CollisionFreeMap;
import io.fluo.recipes.map.CollisionFreeMap.Options;
import org.junit.Assert;
import org.junit.Test;

public class TestGrouping {
  @Test
  public void testTabletGrouping() {
    FluoConfiguration conf = new FluoConfiguration();

    CollisionFreeMap.configure(conf, new Options("m1", "ct", "kt", "vt", 119));
    CollisionFreeMap.configure(conf, new Options("m2", "ct", "kt", "vt", 3));

    ExportQueue.configure(conf, new io.fluo.recipes.export.ExportQueue.Options("eq1", "et", "kt",
        "vt", 7));
    ExportQueue.configure(conf, new io.fluo.recipes.export.ExportQueue.Options("eq2", "et", "kt",
        "vt", 3));

    Pirtos pirtos = CollisionFreeMap.getTableOptimizations(conf.getAppConfiguration());
    pirtos.merge(ExportQueue.getTableOptimizations(conf.getAppConfiguration()));

    Pattern pattern = Pattern.compile(pirtos.getTabletGroupingRegex());

    Assert.assertEquals("m1:u:", group(pattern, "m1:u:f0c"));
    Assert.assertEquals("m1:d:", group(pattern, "m1:d:f0c"));
    Assert.assertEquals("m2:u:", group(pattern, "m2:u:abc"));
    Assert.assertEquals("m2:d:", group(pattern, "m2:d:590"));
    Assert.assertEquals("none", group(pattern, "m3:d:590"));

    Assert.assertEquals("eq1:", group(pattern, "eq1:f0c"));
    Assert.assertEquals("eq2:", group(pattern, "eq2:f0c"));
    Assert.assertEquals("none", group(pattern, "eq3:f0c"));

    // validate the assumptions this test is making
    Assert.assertTrue(pirtos.getSplits().contains(Bytes.of("eq1#")));
    Assert.assertTrue(pirtos.getSplits().contains(Bytes.of("eq2#")));
    Assert.assertTrue(pirtos.getSplits().contains(Bytes.of("eq1:~")));
    Assert.assertTrue(pirtos.getSplits().contains(Bytes.of("eq2:~")));
    Assert.assertTrue(pirtos.getSplits().contains(Bytes.of("m1:u:~")));
    Assert.assertTrue(pirtos.getSplits().contains(Bytes.of("m1:d:~")));
    Assert.assertTrue(pirtos.getSplits().contains(Bytes.of("m2:u:~")));
    Assert.assertTrue(pirtos.getSplits().contains(Bytes.of("m2:d:~")));

    Set<String> expectedGroups =
        ImmutableSet.of("m1:u:", "m1:d:", "m2:u:", "m2:d:", "eq1:", "eq2:");

    // ensure all splits group as expected
    for (Bytes split : pirtos.getSplits()) {
      String g = group(pattern, split.toString());

      if (expectedGroups.contains(g)) {
        Assert.assertTrue(split.toString().startsWith(g));
      } else {
        Assert.assertEquals("none", g);
        Assert.assertTrue(split.toString().equals("eq1#") || split.toString().equals("eq2#"));
      }

    }

  }

  private String group(Pattern pattern, String endRow) {
    Matcher m = pattern.matcher(endRow);
    if (m.matches() && m.groupCount() == 1) {
      return m.group(1);
    }
    return "none";
  }
}
