/*
 * Copyright (C) 2005-2011 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.module.org_alfresco_module_rm.patch;

import java.util.List;
import java.util.Set;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.module.org_alfresco_module_rm.RecordsManagementService;
import org.alfresco.module.org_alfresco_module_rm.capability.Capability;
import org.alfresco.module.org_alfresco_module_rm.capability.CapabilityService;
import org.alfresco.module.org_alfresco_module_rm.dod5015.DOD5015Model;
import org.alfresco.module.org_alfresco_module_rm.model.RecordsManagementModel;
import org.alfresco.module.org_alfresco_module_rm.role.FilePlanRoleService;
import org.alfresco.module.org_alfresco_module_rm.role.Role;
import org.alfresco.repo.module.AbstractModuleComponent;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanNameAware;

/**
 * RM v2.1 patch to updated modified capabilities.
 * 
 * @author Roy Wetherall
 * @since 2.1
 */
public class RMv21CapabilityPatch extends AbstractModuleComponent 
                                  implements BeanNameAware, RecordsManagementModel, DOD5015Model
{
    /** Logger */
    private static Log logger = LogFactory.getLog(RMv21CapabilityPatch.class);  
    
    /** Records management service */
    private RecordsManagementService recordsManagementService;
    
    /** File plan role service */
    private FilePlanRoleService filePlanRoleService;
    
    /** Capability service */
    private CapabilityService capabilityService;
    
    /**
     * @param recordsManagementService  records management service
     */
    public void setRecordsManagementService(RecordsManagementService recordsManagementService)
    {
        this.recordsManagementService = recordsManagementService;
    }

    /**
     * @param filePlanRoleService   file plan role service
     */
    public void setFilePlanRoleService(FilePlanRoleService filePlanRoleService)
    {
        this.filePlanRoleService = filePlanRoleService;
    }
    
    /**
     * @param capabilityService capability service
     */
    public void setCapabilityService(CapabilityService capabilityService)
    {
        this.capabilityService = capabilityService;
    }
    
    /**
     * @see org.alfresco.repo.module.AbstractModuleComponent#executeInternal()
     */
    @Override
    protected void executeInternal() throws Throwable
    {
        if (logger.isDebugEnabled() == true)
        {
            logger.debug("RM module: RMv21CapabilityPatch executing ...");
        }
        
        List<NodeRef> filePlans = recordsManagementService.getFilePlans();
        
        if (logger.isDebugEnabled() == true)
        {
            logger.debug("  ... updating " + filePlans.size() + " file plans");
        }
        
        for (NodeRef filePlan : filePlans)
        {
            if (logger.isDebugEnabled() == true)
            {
                logger.debug("  ... updating file plan " + filePlan.toString());
            }
            
            // add new capabilities
            addCapability(filePlan, 
                          "CreateRecords", 
                          FilePlanRoleService.ROLE_ADMIN, 
                          FilePlanRoleService.ROLE_POWER_USER, 
                          FilePlanRoleService.ROLE_RECORDS_MANAGER,
                          FilePlanRoleService.ROLE_SECURITY_OFFICER);
            addCapability(filePlan,
                          "ManageRules",
                          FilePlanRoleService.ROLE_ADMIN);
            
        }
        
        if (logger.isDebugEnabled() == true)
        {
            logger.debug(" ... complete");
        }
    }   
    
    /**
     * Adds a new capability to the specified roles.
     * 
     * @param filePlan          file plan
     * @param capabilityName    capability name
     * @param roles             roles
     */
    private void addCapability(NodeRef filePlan, String capabilityName, String ... roles)
    {
        Capability capability = capabilityService.getCapability(capabilityName);
        if (capability == null)
        {
            throw new AlfrescoRuntimeException("Unable to bootstrap RMv21 capabilities, because capability " + capabilityName + " does not exist.");
        }
        
        for (String roleName : roles)
        {
            Role role = filePlanRoleService.getRole(filePlan, roleName);
            
            if (role != null)
            {
                // get the roles current capabilities
                Set<Capability> capabilities = role.getCapabilities();
                
                // only update if the capability is missing
                if (capabilities.contains(capability) == false)
                {                
                    if (logger.isDebugEnabled() == true)
                    {
                        logger.debug("  ... adding capability " + capabilityName + " to role " + role.getName());
                    }
                    
                    capabilities.add(capability);
                    filePlanRoleService.updateRole(filePlan, role.getName(), role.getDisplayLabel(), capabilities);
                }
            }            
        }
    }
    
}
