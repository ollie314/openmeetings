/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") +  you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openmeetings.web.user.dashboard;

import static org.apache.openmeetings.persistence.beans.basic.Configuration.RSS_FEED1_KEY;
import static org.apache.openmeetings.persistence.beans.basic.Configuration.RSS_FEED2_KEY;
import static org.apache.openmeetings.rss.LoadAtomRssFeed.getFeedConnection;
import static org.apache.openmeetings.web.app.Application.getBean;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

import org.apache.openmeetings.data.basic.dao.ConfigurationDao;
import org.apache.wicket.behavior.AbstractAjaxBehavior;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.handler.resource.ResourceStreamRequestHandler;
import org.apache.wicket.request.resource.ContentDisposition;
import org.apache.wicket.util.resource.AbstractResourceStream;
import org.apache.wicket.util.resource.ResourceStreamNotFoundException;

import ro.fortsoft.wicket.dashboard.Widget;
import ro.fortsoft.wicket.dashboard.web.WidgetView;

public class RssWidgetView extends WidgetView {
	private static final long serialVersionUID = -6257866996099503210L;
	private RSSFeedBehavior feed1; 
	private RSSFeedBehavior feed2; 

	public RssWidgetView(String id, Model<Widget> model) {
		super(id, model);
		ConfigurationDao cfgDao = getBean(ConfigurationDao.class);
		add(feed1 = new RSSFeedBehavior(cfgDao.getConfValue(RSS_FEED1_KEY, String.class, "")));
		add(feed2 = new RSSFeedBehavior(cfgDao.getConfValue(RSS_FEED2_KEY, String.class, "")));
	}
	
	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);
		response.render(OnDomReadyHeaderItem.forScript("loadRssTab('" + feed1.getCallbackUrl() + "',"
				+ "'" + feed2.getCallbackUrl() + "');")) ;
	}
	
	class RSSFeedBehavior extends AbstractAjaxBehavior {
		private static final long serialVersionUID = 721009368063152450L;
		private String url;

		RSSFeedBehavior(String url) {
			this.url = url;
		}
		
		public void onRequest() {
			ResourceStreamRequestHandler handler = new ResourceStreamRequestHandler(new AbstractResourceStream() {
				private static final long serialVersionUID = -322593118428697261L;
				HttpURLConnection con;
				
				public InputStream getInputStream() throws ResourceStreamNotFoundException {
					try {
						con = getFeedConnection(url);
						con.connect();
						return con.getInputStream();
					} catch (IOException e) {
						throw new ResourceStreamNotFoundException();
					}
				}

				public void close() throws IOException {
					if (con != null) {
						con.disconnect();
					}
				}
			}, "feed");
			handler.setContentDisposition(ContentDisposition.ATTACHMENT);
			getComponent().getRequestCycle().scheduleRequestHandlerAfterCurrent(handler);
		}
	}
}
