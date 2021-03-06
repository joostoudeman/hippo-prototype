/*
 *  Copyright 2012 Finalist B.V.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License")
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.tdclighthouse.prototype.components.json;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import net.sf.json.JSON;
import net.sf.json.JSONObject;

import org.apache.commons.collections.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.hippoecm.hst.component.support.bean.BaseHstComponent;
import org.hippoecm.hst.content.beans.ObjectBeanManagerException;
import org.hippoecm.hst.content.beans.query.HstQuery;
import org.hippoecm.hst.content.beans.query.exceptions.QueryException;
import org.hippoecm.hst.content.beans.standard.HippoBean;
import org.hippoecm.hst.content.beans.standard.HippoBeanIterator;
import org.hippoecm.hst.core.component.HstComponentException;
import org.hippoecm.hst.core.component.HstRequest;
import org.hippoecm.hst.core.component.HstResponse;
import org.hippoecm.hst.core.parameters.ParametersInfo;
import org.onehippo.forge.selection.hst.contentbean.ValueList;
import org.onehippo.forge.selection.hst.contentbean.ValueListItem;

import com.tdclighthouse.prototype.componentsinfo.BlacklistInfo;
import com.tdclighthouse.prototype.componentsinfo.ContentBeanPathInfo;
import com.tdclighthouse.prototype.componentsinfo.ValueListAjaxInfo;
import com.tdclighthouse.prototype.utils.BeanUtils;
import com.tdclighthouse.prototype.utils.Constants;

/**
 * This component provides an AJAX call back which exposes all the lookup lists
 * via JSON. if you do not want to expose a certain lookup, you can black list
 * it via component parameters, notice that you need to use its canonical path.
 * 
 * @author Ebrahim Aharpour
 * 
 */
@ParametersInfo(type = ValueListAjaxInfo.class)
public class ValueListAjax extends BaseHstComponent {

    @Override
    public void doBeforeRender(HstRequest request, HstResponse response) {
        JSON json;
        BlackListChecker blackListChecker = new BlackListChecker(request);
        String path = getPath(request);
        if (StringUtils.isNotBlank(path)) {
            json = getValueListAsJson(request, response, path, blackListChecker);
        } else {
            json = getAvilibleListsAsJson(request, blackListChecker);
        }
        request.setAttribute(Constants.AttributesConstants.JSON, json);
        setCacheTime(request, response);
        response.setContentType(Constants.MimeTypeConstants.APPLICATION_JSON);
    }

    private void setCacheTime(HstRequest request, HstResponse response) {
        double cacheTime = this.<ValueListAjaxInfo> getComponentParametersInfo(request).getCacheTime();
        response.addHeader(Constants.HttpHeaderConstants.AGE, "0");
        response.addHeader(Constants.HttpHeaderConstants.CACHE_CONTROL, Constants.HttpHeaderConstants.MAX_AGE + "="
                + Math.round(cacheTime * 3600));
    }

    protected HippoBeanIterator getAllValueLists(HstRequest request) throws QueryException {
        HippoBean scope = BeanUtils.getContentBeanViaParameters(this
                .<ContentBeanPathInfo> getComponentParametersInfo(request));
        @SuppressWarnings("unchecked")
        HstQuery query = request.getRequestContext().getQueryManager().createQuery(scope, ValueList.class);
        return query.execute().getHippoBeans();
    }

    protected JSON getAvilibleListsAsJson(HstRequest request, BlackListChecker blackListChecker) {
        try {
            HippoBeanIterator valueLists = getAllValueLists(request);
            return valueListIteratorToJson(valueLists, blackListChecker);
        } catch (QueryException e) {
            throw new HstComponentException(e);
        }
    }

    protected String getPath(HstRequest request) {
        String path = null;
        path = request.getRequestContext().getResolvedSiteMapItem().getParameter(Constants.ParametersConstants.PATH);
        if (StringUtils.isBlank(path)) {
            path = getPublicRequestParameter(request, Constants.ParametersConstants.PATH);
        }

        if (StringUtils.isNotBlank(path) && !path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    protected JSON getValueListAsJson(HstRequest request, HstResponse response, String path,
            BlackListChecker blackListChecker) {
        try {
            JSONObject json = new JSONObject();
            Object object = request.getRequestContext().getObjectBeanManager().getObject(path);
            if (object instanceof ValueList) {
                ValueList bean = (ValueList) object;
                if (!blackListChecker.isBlackListed(bean)) {
                    List<ValueListItem> listItem = bean.getItems();
                    for (ValueListItem listItemBean : listItem) {
                        json.put(listItemBean.getKey(), listItemBean.getLabel());
                    }
                } else {
                    setErrorMessage(json, "Forbidden");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                }
            } else {
                setErrorMessage(json, "Not Found");
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            }
            return json;
        } catch (ObjectBeanManagerException e) {
            throw new HstComponentException(e);
        }
    }

    protected void setErrorMessage(JSONObject json, String message) {
        json.clear();
        json.put(Constants.AttributesConstants.ERROR_MESSAGE, message);
    }

    protected JSON valueListIteratorToJson(HippoBeanIterator valueLists, BlackListChecker blackListChecker) {
        JSONObject json = new JSONObject();

        while (valueLists.hasNext()) {
            HippoBean hippoBean = valueLists.nextHippoBean();
            if ((hippoBean instanceof ValueList) && !blackListChecker.isBlackListed(hippoBean)) {
                json.put(hippoBean.getName(), hippoBean.getPath());
            }
        }
        return json;
    }

    private class BlackListChecker {

        private final Set<String> blackList;

        @SuppressWarnings("unchecked")
        public BlackListChecker(HstRequest request) {
            Set<String> set = new HashSet<String>();
            BlacklistInfo parametersInfo = getComponentParametersInfo(request);
            String blacklisted = parametersInfo.getBlacklisted();
            String[] list = blacklisted.split("\\s*,\\s*");
            for (String item : list) {
                set.add(item);
            }
            this.blackList = SetUtils.unmodifiableSet(set);
        }

        private boolean isBlackListed(HippoBean hippoBean) {
            String canonicalPath = hippoBean.getCanonicalPath();
            return blackList.contains(canonicalPath);
        }
    }
}
