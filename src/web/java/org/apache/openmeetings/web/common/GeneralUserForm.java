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
package org.apache.openmeetings.web.common;

import static org.apache.openmeetings.db.util.UserHelper.getMinPasswdLength;
import static org.apache.openmeetings.web.app.Application.getBean;
import static org.apache.openmeetings.web.app.WebSession.AVAILABLE_TIMEZONES;
import static org.apache.openmeetings.web.app.WebSession.getLanguage;
import static org.apache.wicket.validation.validator.StringValidator.minimumLength;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.openmeetings.db.dao.basic.ConfigurationDao;
import org.apache.openmeetings.db.dao.user.OrganisationDao;
import org.apache.openmeetings.db.dao.user.SalutationDao;
import org.apache.openmeetings.db.dao.user.StateDao;
import org.apache.openmeetings.db.dao.user.UserDao;
import org.apache.openmeetings.db.entity.user.Organisation;
import org.apache.openmeetings.db.entity.user.Organisation_Users;
import org.apache.openmeetings.db.entity.user.Salutation;
import org.apache.openmeetings.db.entity.user.State;
import org.apache.openmeetings.db.entity.user.User;
import org.apache.openmeetings.util.CalendarHelper;
import org.apache.openmeetings.web.app.Application;
import org.apache.openmeetings.web.app.WebSession;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.core.request.handler.IPartialPageRequestHandler;
import org.apache.wicket.extensions.validation.validator.RfcCompliantEmailAddressValidator;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.RequiredTextField;
import org.apache.wicket.markup.html.form.TextArea;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.IMarkupSourcingStrategy;
import org.apache.wicket.markup.html.panel.PanelMarkupSourcingStrategy;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.util.string.Strings;
import org.threeten.bp.LocalDate;
import org.wicketstuff.select2.Response;
import org.wicketstuff.select2.Select2MultiChoice;
import org.wicketstuff.select2.TextChoiceProvider;

import com.googlecode.wicket.kendo.ui.form.datetime.local.AjaxDatePicker;
import com.googlecode.wicket.kendo.ui.resource.KendoGlobalizeResourceReference;

public class GeneralUserForm extends Form<User> {
	private static final long serialVersionUID = 1L;
	private Salutation salutation;
	private LocalDate age;
	private PasswordTextField passwordField;
	private RequiredTextField<String> email;

	public GeneralUserForm(String id, IModel<User> model, boolean isAdminForm) {
		super(id, model);

		//TODO should throw exception if non admin User edit somebody else (or make all fields read-only)
		add(passwordField = new PasswordTextField("password", new Model<String>()));
		ConfigurationDao cfgDao = getBean(ConfigurationDao.class);
		passwordField.setRequired(false).add(minimumLength(getMinPasswdLength(cfgDao)));

		updateModelObject(getModelObject());
		SalutationDao salutDao = getBean(SalutationDao.class);
		add(new DropDownChoice<Salutation>("salutation"
				, new PropertyModel<Salutation>(this, "salutation")
				, salutDao.getUserSalutations(getLanguage())
				, new ChoiceRenderer<Salutation>() {
					private static final long serialVersionUID = 1L;

					@Override
					public Object getDisplayValue(Salutation object) {
						return getString("" + object.getFieldvalues_id());
					}

					@Override
					public String getIdValue(Salutation object, int index) {
						return "" + object.getSalutations_id();
					}
				})
			.add(new AjaxFormComponentUpdatingBehavior("change") {
				private static final long serialVersionUID = 1L;

				@Override
				protected void onUpdate(AjaxRequestTarget target) {
					GeneralUserForm.this.getModelObject().setSalutations_id(salutation.getSalutations_id());
				}
			}));
		add(new TextField<String>("firstname"));
		add(new TextField<String>("lastname"));
		
		add(new DropDownChoice<String>("timeZoneId", AVAILABLE_TIMEZONES));

		add(new LanguageDropDown("language_id"));

		add(email = new RequiredTextField<String>("adresses.email"));
		email.setLabel(Model.of(Application.getString(137)));
		email.add(RfcCompliantEmailAddressValidator.getInstance());
		add(new TextField<String>("adresses.phone"));
		add(new CheckBox("sendSMS"));
		add(new AjaxDatePicker("age", new PropertyModel<LocalDate>(this, "age"), WebSession.get().getLocale()) {
			private static final long serialVersionUID = 1L;

			@Override
			public void onValueChanged(IPartialPageRequestHandler target) {
				User u = GeneralUserForm.this.getModelObject();
				u.setAge(CalendarHelper.getDate(age, u.getTimeZoneId()));
			}
		});
		add(new TextField<String>("adresses.street"));
		add(new TextField<String>("adresses.additionalname"));
		add(new TextField<String>("adresses.zip"));
		add(new TextField<String>("adresses.town"));
		add(new DropDownChoice<State>("adresses.states", getBean(StateDao.class).getStates(), new ChoiceRenderer<State>("name", "state_id")));
		add(new TextArea<String>("adresses.comment"));

		final List<Organisation_Users> orgUsers;
		if (isAdminForm) {
			List<Organisation> orgList = getBean(OrganisationDao.class).get(0, Integer.MAX_VALUE);
			orgUsers = new ArrayList<Organisation_Users>(orgList.size());
			for (Organisation org : orgList) {
				orgUsers.add(new Organisation_Users(org));
			}
		} else {
			orgUsers = getModelObject().getOrganisation_users();
		}
		add(new Select2MultiChoice<Organisation_Users>("organisation_users", null, new TextChoiceProvider<Organisation_Users>() {
			private static final long serialVersionUID = 1L;

			@Override
			protected String getDisplayText(Organisation_Users choice) {
				return choice.getOrganisation().getName();
			}

			@Override
			protected Object getId(Organisation_Users choice) {
				return choice.getOrganisation().getOrganisation_id();
			}

			@Override
			public void query(String term, int page, Response<Organisation_Users> response) {
				for (Organisation_Users ou : orgUsers) {
					if (Strings.isEmpty(term) || (!Strings.isEmpty(term) && ou.getOrganisation().getName().contains(term))) {
						response.add(ou);
					}
				}
			}

			@Override
			public Collection<Organisation_Users> toChoices(Collection<String> _ids) {
				List<Long> ids = new ArrayList<Long>();
				for (String id : _ids) {
					ids.add(Long.parseLong(id));
				}
				List<Organisation_Users> list = new ArrayList<Organisation_Users>();
				for (Organisation o : getBean(OrganisationDao.class).get(ids)) {
					list.add(new Organisation_Users(o));
				}
				return list;
			}
		}).setEnabled(isAdminForm));
	}

	public void updateModelObject(User u) {
		salutation = getBean(SalutationDao.class).get(u.getSalutations_id(), getLanguage());
		age = CalendarHelper.getDate(u.getAge(), u.getTimeZoneId());
	}
	
	@Override
	protected void onValidate() {
		User u = getModelObject();
		if(!getBean(UserDao.class).checkEmail(email.getConvertedInput(), u.getType(), u.getDomainId(), u.getUser_id())) {
			error(Application.getString(1000));
		}
		super.onValidate();
	}
	
	public PasswordTextField getPasswordField() {
		return passwordField;
	}

	
	public String getEmail() {
		return email.getValue();
	}
	
	@Override
	protected IMarkupSourcingStrategy newMarkupSourcingStrategy() {
		return new PanelMarkupSourcingStrategy(false);
	}

	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);
		response.render(JavaScriptHeaderItem.forReference(new KendoGlobalizeResourceReference(WebSession.get().getLocale())));
	}
}
