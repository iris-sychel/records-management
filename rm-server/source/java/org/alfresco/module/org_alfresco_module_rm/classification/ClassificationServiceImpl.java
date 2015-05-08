/*
 * Copyright (C) 2005-2015 Alfresco Software Limited.
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
package org.alfresco.module.org_alfresco_module_rm.classification;

import static org.alfresco.module.org_alfresco_module_rm.util.RMParameterCheck.checkNotBlank;
import static org.alfresco.util.ParameterCheck.mandatory;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.alfresco.model.ContentModel;
import org.alfresco.module.org_alfresco_module_rm.classification.ClassificationServiceException.InvalidNode;
import org.alfresco.module.org_alfresco_module_rm.classification.ClassificationServiceException.LevelIdNotFound;
import org.alfresco.module.org_alfresco_module_rm.classification.ClassificationServiceException.MissingConfiguration;
import org.alfresco.module.org_alfresco_module_rm.classification.ClassificationServiceException.ReasonIdNotFound;
import org.alfresco.module.org_alfresco_module_rm.classification.model.ClassifiedContentModel;
import org.alfresco.module.org_alfresco_module_rm.util.ServiceBaseImpl;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.attributes.AttributeService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Neil Mc Erlean
 * @since 3.0
 */
public class ClassificationServiceImpl extends ServiceBaseImpl
                                       implements ClassificationService, ClassifiedContentModel
{
    private static final Serializable[] LEVELS_KEY = new String[] { "org.alfresco",
                                                              "module.org_alfresco_module_rm",
                                                              "classification.levels" };
    private static final Serializable[] REASONS_KEY = new String[] { "org.alfresco",
                                                               "module.org_alfresco_module_rm",
                                                               "classification.reasons" };
    private static final Logger LOGGER = LoggerFactory.getLogger(ClassificationServiceImpl.class);

    private AttributeService attributeService; // TODO What about other code (e.g. REST API) accessing the AttrService?
    private NodeService nodeService;
    private SecurityClearanceService securityClearanceService;
    private ClassificationServiceDAO classificationServiceDao;

    /** The classification levels currently configured in this server. */
    private ClassificationLevelManager levelManager;
    /** The classification reasons currently configured in this server. */
    private ClassificationReasonManager reasonManager;

    public void setAttributeService(AttributeService service) { this.attributeService = service; }
    public void setNodeService(NodeService service) { this.nodeService = service; }
    public void setSecurityClearanceService(SecurityClearanceService service) { this.securityClearanceService = service; }

    /** Set the object from which configuration options will be read. */
    public void setClassificationServiceDAO(ClassificationServiceDAO classificationServiceDao) { this.classificationServiceDao = classificationServiceDao; }

    void initConfiguredClassificationLevels()
    {
        final List<ClassificationLevel> allPersistedLevels  = getPersistedLevels();
        final List<ClassificationLevel> configurationLevels = getConfigurationLevels();

        // Note! We cannot log the level names or even the size of these lists for security reasons.
        LOGGER.debug("Persisted classification levels: {}", loggableStatusOf(allPersistedLevels));
        LOGGER.debug("Classpath classification levels: {}", loggableStatusOf(configurationLevels));

        if (configurationLevels == null || configurationLevels.isEmpty())
        {
            throw new MissingConfiguration("Classification level configuration is missing.");
        }
        else if (!configurationLevels.equals(allPersistedLevels))
        {
            attributeService.setAttribute((Serializable) configurationLevels, LEVELS_KEY);
            this.levelManager = new ClassificationLevelManager(configurationLevels);
        }
        else
        {
            this.levelManager = new ClassificationLevelManager(allPersistedLevels);
        }
    }

    void initConfiguredClassificationReasons()
    {
        final List<ClassificationReason> persistedReasons = getPersistedReasons();
        final List<ClassificationReason> classpathReasons = getConfigurationReasons();

        // Note! We cannot log the reasons or even the size of these lists for security reasons.
        LOGGER.debug("Persisted classification reasons: {}", loggableStatusOf(persistedReasons));
        LOGGER.debug("Classpath classification reasons: {}", loggableStatusOf(classpathReasons));

        if (isEmpty(persistedReasons))
        {
            if (isEmpty(classpathReasons))
            {
                throw new MissingConfiguration("Classification reason configuration is missing.");
            }
            attributeService.setAttribute((Serializable) classpathReasons, REASONS_KEY);
            this.reasonManager = new ClassificationReasonManager(classpathReasons);
        }
        else
        {
            if (isEmpty(classpathReasons) || !classpathReasons.equals(persistedReasons))
            {
                LOGGER.warn("Classification reasons configured in classpath do not match those stored in Alfresco. "
                            + "Alfresco will use the unchanged values stored in the database.");
                // RM-2073 says that we should log a warning and proceed normally.
            }
            this.reasonManager = new ClassificationReasonManager(persistedReasons);
        }
    }

    private static boolean isEmpty(List<?> l) { return l == null || l.isEmpty(); }

    /** Helper method for debug-logging of sensitive lists. */
    private String loggableStatusOf(List<?> l)
    {
        if      (l == null)   { return "null"; }
        else if (l.isEmpty()) { return "empty"; }
        else                  { return "non-empty"; }
    }

    /**
     * Gets the list (in descending order) of classification levels - as persisted in the system.
     * @return the list of classification levels if they have been persisted, else {@code null}.
     */
    List<ClassificationLevel> getPersistedLevels()
    {
        return authenticationUtil.runAsSystem(new AuthenticationUtil.RunAsWork<List<ClassificationLevel>>()
        {
            @Override
            @SuppressWarnings("unchecked")
            public List<ClassificationLevel> doWork() throws Exception
            {
                return (List<ClassificationLevel>) attributeService.getAttribute(LEVELS_KEY);
            }
        });
    }

    /** Gets the list (in descending order) of classification levels - as defined in the system configuration. */
    List<ClassificationLevel> getConfigurationLevels()
    {
        return classificationServiceDao.getConfiguredLevels();
    }

    /**
     * Gets the list of classification reasons as persisted in the system.
     * @return the list of classification reasons if they have been persisted, else {@code null}.
     */
    List<ClassificationReason> getPersistedReasons()
    {
        return authenticationUtil.runAsSystem(new AuthenticationUtil.RunAsWork<List<ClassificationReason>>()
        {
            @Override
            @SuppressWarnings("unchecked")
            public List<ClassificationReason> doWork() throws Exception
            {
                return (List<ClassificationReason>) attributeService.getAttribute(REASONS_KEY);
            }
        });
    }

    /** Gets the list of classification reasons - as defined and ordered in the system configuration. */
    List<ClassificationReason> getConfigurationReasons()
    {
        return classificationServiceDao.getConfiguredReasons();
    }

    /**
     * Create a list containing all classification levels up to and including the supplied level.
     *
     * @param allLevels The list of all the classification levels starting with the highest security.
     * @param targetLevel The highest security classification level that should be returned. If this is not found then
     *            an empty list will be returned.
     * @return an immutable list of the levels that a user at the target level can see.
     */
    List<ClassificationLevel> restrictList(List<ClassificationLevel> allLevels, ClassificationLevel targetLevel)
    {
        int targetIndex = allLevels.indexOf(targetLevel);
        if (targetIndex == -1) { return Collections.emptyList(); }
        List<ClassificationLevel> subList = allLevels.subList(targetIndex, allLevels.size());
        return Collections.unmodifiableList(subList);
    }

    @Override
    public List<ClassificationLevel> getClassificationLevels()
    {
        if (levelManager == null)
        {
            return Collections.emptyList();
        }
        ClassificationLevel usersLevel = securityClearanceService.getUserSecurityClearance().getClearanceLevel();
        return restrictList(levelManager.getClassificationLevels(), usersLevel);
    }

    @Override public List<ClassificationReason> getClassificationReasons()
    {
        return reasonManager == null ? Collections.<ClassificationReason>emptyList() :
                Collections.unmodifiableList(reasonManager.getClassificationReasons());
    }

    @Override
    public void classifyContent(String classificationLevelId, String classificationAuthority,
                Set<String> classificationReasonIds, NodeRef content)
    {
        checkNotBlank("classificationLevelId", classificationLevelId);
        checkNotBlank("classificationAuthority", classificationAuthority);
        mandatory("classificationReasonIds", classificationReasonIds);
        mandatory("content", content);

        if (!dictionaryService.isSubClass(nodeService.getType(content), ContentModel.TYPE_CONTENT))
        {
            throw new InvalidNode(content, "The supplied node is not a content node.");
        }
        if (nodeService.hasAspect(content, ASPECT_CLASSIFIED))
        {
            throw new UnsupportedOperationException(
                        "The content has already been classified. Reclassification is currently not supported.");
        }

        Map<QName, Serializable> properties = new HashMap<QName, Serializable>();
        // Check the classification level id - an exception will be thrown if the id cannot be found
        getClassificationLevelById(classificationLevelId);

        // Initial classification id
        if (nodeService.getProperty(content, PROP_INITIAL_CLASSIFICATION) == null)
        {
            properties.put(PROP_INITIAL_CLASSIFICATION, classificationLevelId);
        }

        // Current classification id
        properties.put(PROP_CURRENT_CLASSIFICATION, classificationLevelId);

        // Classification authority
        properties.put(PROP_CLASSIFICATION_AUTHORITY, classificationAuthority);

        // Classification reason ids
        HashSet<String> classificationReasons = new HashSet<>();
        for (String classificationReasonId : classificationReasonIds)
        {
            // Check the classification reason id - an exception will be thrown if the id cannot be found
            getClassificationReasonById(classificationReasonId);
            classificationReasons.add(classificationReasonId);
        }
        properties.put(PROP_CLASSIFICATION_REASONS, classificationReasons);

        // Add aspect
        nodeService.addAspect(content, ASPECT_CLASSIFIED, properties);
    }

    @Override public ClassificationLevel getDefaultClassificationLevel()
    {
        List<ClassificationLevel> classificationLevels = getClassificationLevels();
        return classificationLevels.isEmpty() ? null : classificationLevels.get(classificationLevels.size() - 1);
    }

    /**
     * @see org.alfresco.module.org_alfresco_module_rm.classification.ClassificationService#getClassificationLevelById(java.lang.String)
     */
    @Override
    public ClassificationLevel getClassificationLevelById(String classificationLevelId) throws LevelIdNotFound
    {
        checkNotBlank("classificationLevelId", classificationLevelId);
        return levelManager.findLevelById(classificationLevelId);
    }

    /**
     * @see org.alfresco.module.org_alfresco_module_rm.classification.ClassificationService#getClassificationReasonById(java.lang.String)
     */
    @Override
    public ClassificationReason getClassificationReasonById(String classificationReasonId) throws ReasonIdNotFound
    {
        checkNotBlank("classificationReasonId", classificationReasonId);
        return reasonManager.findReasonById(classificationReasonId);
    }
}
