package com.tdclighthouse.prototype.components;

import java.util.concurrent.Callable;

import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.hippoecm.hst.cache.CacheElement;
import org.hippoecm.hst.cache.HstCache;
import org.hippoecm.hst.cache.ehcache.CacheElementEhCache;
import org.hippoecm.hst.configuration.sitemap.HstSiteMapItem;
import org.hippoecm.hst.configuration.sitemenu.HstSiteMenuConfigurationService;
import org.hippoecm.hst.configuration.sitemenu.HstSiteMenusConfiguration;
import org.hippoecm.hst.core.component.HstComponentException;
import org.hippoecm.hst.core.component.HstRequest;
import org.hippoecm.hst.core.component.HstResponse;
import org.hippoecm.hst.core.request.HstRequestContext;
import org.hippoecm.hst.core.sitemenu.EditableMenu;
import org.hippoecm.hst.core.sitemenu.HstSiteMenu;
import org.hippoecm.hst.site.HstServices;

import com.tdclighthouse.prototype.beans.compounds.CacheableSiteMenu;
import com.tdclighthouse.prototype.componentsinfo.NavigationInfo;
import com.tdclighthouse.prototype.provider.RepoBasedMenuProvider;
import com.tdclighthouse.prototype.utils.BeanUtils;
import com.tdclighthouse.prototype.utils.Constants.AttributesConstants;

public class CachedNavigation extends WebDocumentDetail {
    @Override
    public void doBeforeRender(final HstRequest request, final HstResponse response) {
        try {
            super.doBeforeRender(request, response);
            HstCache cache = HstServices.getComponentManager().getComponent("pageCache");
            Key key = getCacheKey(request);
            CacheElement cacheElement = cache.get(key, new Callback(request));

            request.setAttribute(AttributesConstants.MENU, cacheElement.getContent());
            request.setAttribute(AttributesConstants.PARAM_INFO, getComponentParametersInfo(request));
            request.setAttribute(AttributesConstants.LABELS, BeanUtils.getLabels(getComponentParametersInfo(request)));
        } catch (Exception e) {
            throw new HstComponentException(e.getMessage(), e);
        }

    }

    private Key getCacheKey(HstRequest request) {
        HstRequestContext requestContext = request.getRequestContext();
        HstSiteMapItem selectedSiteMapItem = requestContext.getResolvedSiteMapItem().getHstSiteMapItem();
        HstSiteMenusConfiguration siteMenusConfiguration = selectedSiteMapItem.getHstSiteMap().getSite()
                .getSiteMenusConfiguration();
        String menuName = getComponentParameters(NavigationInfo.MENU_NAME, NavigationInfo.MENU_NAME_DEFAULT,
                String.class);
        HstSiteMenuConfigurationService service = ((HstSiteMenuConfigurationService) siteMenusConfiguration
                .getSiteMenuConfiguration(menuName));
        return new Key(service.getCanonicalPath());
    }

    private class Callback implements Callable<CacheElementEhCache> {

        private final HstRequest request;

        public Callback(HstRequest request) {
            this.request = request;
        }

        @Override
        public CacheElementEhCache call() throws Exception {
            EditableMenu editableMenu = null;
            String menuName = getComponentParameters(NavigationInfo.MENU_NAME, NavigationInfo.MENU_NAME_DEFAULT,
                    String.class);
            HstSiteMenu menu = request.getRequestContext().getHstSiteMenus().getSiteMenu(menuName);
            if (menu != null) {
                editableMenu = menu.getEditableMenu();
                boolean showFacet = getComponentParameters(NavigationInfo.SHOW_FACETED_NAVIGATION,
                        NavigationInfo.SHOW_FACETED_NAVIGATION_DEFAULT, Boolean.class);
                boolean userIndexDocument = getComponentParameters(NavigationInfo.USE_INDEX_DOCUMENT,
                        NavigationInfo.USE_INDEX_DOCUMENT_DEFAULT, Boolean.class);
                new RepoBasedMenuProvider(request.getRequestContext().getSiteContentBaseBean(), showFacet,
                        userIndexDocument, request).addRepoBasedMenuItems(editableMenu);
            }
            return new CacheElementEhCache(getCacheKey(request), new CacheableSiteMenu(editableMenu));
        }

    };

    private static class Key {
        private String name;

        public Key(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Key && this.name.equals(((Key) obj).name);
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(31, 41).append(name).hashCode();
        }

    }

}
