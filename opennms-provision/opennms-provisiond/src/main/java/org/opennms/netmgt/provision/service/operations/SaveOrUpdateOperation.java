/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2006-2022 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.provision.service.operations;

import java.net.InetAddress;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.opennms.netmgt.dao.api.MonitoringLocationDao;
import org.opennms.netmgt.model.OnmsCategory;
import org.opennms.netmgt.model.OnmsIpInterface;
import org.opennms.netmgt.model.OnmsMonitoredService;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsNode.NodeLabelSource;
import org.opennms.netmgt.model.OnmsNode.NodeType;
import org.opennms.netmgt.model.OnmsServiceType;
import org.opennms.netmgt.model.PrimaryType;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;
import org.opennms.netmgt.provision.service.ProvisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyAccessorFactory;

import com.google.common.base.Strings;

public abstract class SaveOrUpdateOperation extends ImportOperation {
    private static final Logger LOG = LoggerFactory.getLogger(SaveOrUpdateOperation.class);

    private final OnmsNode m_node;
    private final String monitorKey;
    private OnmsIpInterface m_currentInterface;
    private OnmsMonitoredService m_currentService;
    
    private ScanManager m_scanManager;
    private String m_rescanExisting = Boolean.TRUE.toString();

    protected SaveOrUpdateOperation(Integer nodeId, String foreignSource, String foreignId, String nodeLabel, String location, String building, String city, ProvisionService provisionService, String rescanExisting, String monitorKey) {
        super(provisionService);

        m_node = new OnmsNode();

        m_node.setLocation(Strings.isNullOrEmpty(location) ?
                new OnmsMonitoringLocation(MonitoringLocationDao.DEFAULT_MONITORING_LOCATION_ID, MonitoringLocationDao.DEFAULT_MONITORING_LOCATION_ID) :
                new OnmsMonitoringLocation(location, location));

        m_node.setLabel(nodeLabel);
        m_node.setId(nodeId);
        m_node.setLabelSource(NodeLabelSource.USER);
        m_node.setType(NodeType.ACTIVE);
        m_node.setForeignSource(foreignSource);
        m_node.setForeignId(foreignId);
        m_node.getAssetRecord().setBuilding(building);
        m_node.getAssetRecord().setCity(city);
        m_rescanExisting = rescanExisting;
        this.monitorKey = monitorKey;
    }
    
    /**
     * <p>getScanManager</p>
     *
     * @return a {@link org.opennms.netmgt.provision.service.operations.ScanManager} object.
     */
    public ScanManager getScanManager() {
        return m_scanManager;
    }

    /**
     * <p>foundInterface</p>
     *
     * @param ipAddr a {@link java.lang.String} object.
     * @param descr a {@link java.lang.Object} object.
     * @param primaryType a {@link InterfaceSnmpPrimaryType} object.
     * @param managed a boolean.
     * @param status a int.
     * @param dnsLookups the list of DNS lookup futures.
     */
    public void foundInterface(InetAddress addr, Object descr, final PrimaryType primaryType, boolean managed, int status, final Set<CompletableFuture<Void>> dnsLookups) {
        
        if (addr == null) {
            LOG.error("Found interface on node {} with an empty/invalid ipaddr! Ignoring!", m_node.getLabel());
            return;
        }

        m_currentInterface = new OnmsIpInterface(addr, m_node);
        m_currentInterface.setIsManaged(status == 3 ? "U" : "M");
        m_currentInterface.setIsSnmpPrimary(primaryType);

        if (PrimaryType.PRIMARY.equals(primaryType)) {
            if (addr != null) {
                m_scanManager = new ScanManager(getProvisionService().getLocationAwareSnmpClient(), addr);
            }
        }

        //FIXME: verify this doesn't conflict with constructor.  The constructor already adds this
        //interface to the node.
        m_node.addIpInterface(m_currentInterface);

        if (System.getProperty("org.opennms.provisiond.reverseResolveRequisitionIpInterfaceHostnames", "true").equalsIgnoreCase("true")) {
            dnsLookups.add(getProvisionService().getHostnameResolver().getHostnameAsync(addr, m_node.getLocation().getLocationName()).thenAccept(s -> m_node.getInterfaceWithAddress(addr).setIpHostName(s)));
        }
    }
	
    /**
     * <p>scan</p>
     */
    @Override
    public void scan() {
    	updateSnmpData();
    }
	
    /**
     * <p>updateSnmpData</p>
     */
    protected void updateSnmpData() {
        if (m_scanManager != null) {
            m_scanManager.updateSnmpData(m_node, getProvisionService().getHostnameResolver());
        }
	}

    /**
     * <p>foundMonitoredService</p>
     *
     * @param serviceName a {@link java.lang.String} object.
     */
    public void foundMonitoredService(String serviceName) {
        // current interface may be null if it has no ipaddr
        if (m_currentInterface != null) {
            OnmsServiceType svcType = getProvisionService().createServiceTypeIfNecessary(serviceName);
            m_currentService = new OnmsMonitoredService(m_currentInterface, svcType);
            m_currentService.setStatus("A");
            m_currentInterface.getMonitoredServices().add(m_currentService);
        }
    
    }

    /**
     * <p>foundCategory</p>
     *
     * @param name a {@link java.lang.String} object.
     */
    public void foundCategory(String name) {
        OnmsCategory category = getProvisionService().createCategoryIfNecessary(name);
        m_node.getCategories().add(category);
    }

    /**
     * <p>getNode</p>
     *
     * @return a {@link org.opennms.netmgt.model.OnmsNode} object.
     */
    protected OnmsNode getNode() {
        return m_node;
    }

    protected String getRescanExisting() {
        return m_rescanExisting;
    }

    public String getMonitorKey() {
        return monitorKey;
    }

    /**
     * <p>foundAsset</p>
     *
     * @param name a {@link java.lang.String} object.
     * @param value a {@link java.lang.String} object.
     */
    public void foundAsset(final String name, final String value) {
        final BeanWrapper w = PropertyAccessorFactory.forBeanPropertyAccess(m_node.getAssetRecord());
        try {
            w.setPropertyValue(name, value);
        } catch (final BeansException e) {
            LOG.warn("Could not set property on object of type {}: {}", m_node.getClass().getName(), name, e);
        }
    }

    public void foundNodeMetaData(String context, String key, String value) {
        m_node.addMetaData(context, key, value);
    }

    public void foundInterfaceMetaData(String context, String key, String value) {
        m_currentInterface.addMetaData(context, key, value);
    }

    public void foundServiceMetaData(String context, String key, String value) {
        m_currentService.addMetaData(context, key, value);
    }

    @Override
    public OperationType getOperationType() {
        return OperationType.UPDATE;
    }

}
