/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
//copied by Roman Krajewski to be able to load GraphHopper configs without Graphhopper-Web-Bundle and Web-API dependencies
package jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.config.LMProfile;
import com.graphhopper.config.Profile;

public class GraphHopperConfigModule extends SimpleModule {

    public GraphHopperConfigModule() {
        setMixInAnnotation(Profile.class, ProfileMixIn.class);
        setMixInAnnotation(LMProfile.class, LMProfileMixIn.class);
        setMixInAnnotation(GraphHopperConfig.class, GraphHopperConfigMixIn.class);
    }

}
