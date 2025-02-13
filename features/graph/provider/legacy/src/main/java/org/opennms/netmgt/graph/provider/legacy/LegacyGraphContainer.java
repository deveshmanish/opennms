/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2022 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2022 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.graph.provider.legacy;

import java.util.List;
import java.util.stream.Collectors;

import org.opennms.features.topology.api.topo.GraphProvider;
import org.opennms.features.topology.api.topo.MetaTopologyProvider;
import org.opennms.netmgt.graph.api.ImmutableGraphContainer;
import org.opennms.netmgt.graph.api.generic.GenericGraphContainer;
import org.opennms.netmgt.graph.api.info.GraphInfo;

public class LegacyGraphContainer implements ImmutableGraphContainer<LegacyGraph> {
    private final MetaTopologyProvider delegate;
    private final String id;
    private final String label;
    private final String description;
    
    public LegacyGraphContainer(MetaTopologyProvider delegate, String id, String label, String description) {
        this.delegate = delegate;
        this.id = id;
        this.label = label;
        this.description = description;
    }

    @Override
    public List<LegacyGraph> getGraphs() {
        return delegate.getGraphProviders().stream().map(LegacyGraph::getLegacyGraphFromTopoGraphProvider).collect(Collectors.toList());
    }

    @Override
    public LegacyGraph getGraph(String namespace) {
        return LegacyGraph.getLegacyGraphFromTopoGraphProvider(delegate.getGraphProviderBy(namespace));
    }

    @Override
    public GenericGraphContainer asGenericGraphContainer() {
        GenericGraphContainer.GenericGraphContainerBuilder builder = GenericGraphContainer.builder()
            .id(id)
            .label(label)
            .description(description);
        for (LegacyGraph graph: getGraphs()) {
            builder.addGraph(graph.asGenericGraph());
        }
        return builder.build();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public List<String> getNamespaces() {
        return delegate.getGraphProviders().stream().map(GraphProvider::getNamespace).collect(Collectors.toList());
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public GraphInfo getGraphInfo(String namespace) {
        return LegacyGraph.getGraphInfo(delegate.getGraphProviderBy(namespace));
    }

    @Override
    public GraphInfo getPrimaryGraphInfo() {
        return LegacyGraph.getGraphInfo(delegate.getDefaultGraphProvider());
    }

    @Override
    public List<GraphInfo> getGraphInfos() {
        return delegate.getGraphProviders().stream().map(LegacyGraph::getGraphInfo).collect(Collectors.toList());
    }
}
