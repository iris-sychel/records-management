/*
 * Copyright (C) 2005-2014 Alfresco Software Limited.
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
package org.alfresco.po.rm;

import org.alfresco.po.share.site.contentrule.FolderRulesPage;
import org.alfresco.webdrone.RenderTime;
import org.alfresco.webdrone.WebDrone;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Extends {@link FolderRulesPage} to add RM specific methods
 *
 * @author Tuna Aksoy
 * @since 2.2
 */
public class RmFolderRulesPage extends FolderRulesPage
{

    private static final By LINK_CREATE_RULE_PAGE_SELECTOR = By.cssSelector("div[class=dialog-option] a[href*='rule-edit']");

    public RmFolderRulesPage(WebDrone drone)
    {
        super(drone);
    }

    @SuppressWarnings("unchecked")
    @Override
    public RmFolderRulesPage render(RenderTime timer)
    {
        return (RmFolderRulesPage) super.render(timer);
    }

    @SuppressWarnings("unchecked")
    @Override
    public RmFolderRulesPage render(long time)
    {
        return (RmFolderRulesPage) super.render(time);
    }

    @SuppressWarnings("unchecked")
    @Override
    public RmFolderRulesPage render()
    {
        return (RmFolderRulesPage) super.render();
    }

    public RmCreateRulePage openCreateRulePage()
    {
        WebElement element = drone.findAndWait(LINK_CREATE_RULE_PAGE_SELECTOR);
        element.click();
        return drone.getCurrentPage().render();
    }
}