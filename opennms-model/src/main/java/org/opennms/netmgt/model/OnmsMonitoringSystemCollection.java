/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2012-2023 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2023 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.model;

import java.util.Collection;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;
import org.opennms.core.config.api.JaxbListWrapper;

@XmlRootElement(name="monitoringSystems")
@JsonRootName("monitoringSystems")
public class OnmsMonitoringSystemCollection extends JaxbListWrapper<OnmsMonitoringSystem> {

    private static final long serialVersionUID = -5250100085101434238L;

    public OnmsMonitoringSystemCollection() { super(); }

    public OnmsMonitoringSystemCollection(final Collection<? extends OnmsMonitoringSystem> objects) {
        super(objects);
    }

    @Override
    @XmlElement(name="monitoringSystem")
    @JsonProperty("monitoringSystem")
    public List<OnmsMonitoringSystem> getObjects() {
        return super.getObjects();
    }
}
