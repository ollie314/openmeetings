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
package org.apache.openmeetings.backup;

import static org.apache.openmeetings.util.OpenmeetingsVariables.webAppRootKey;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.transaction.util.FileHelper;
import org.apache.openmeetings.db.dao.basic.ChatDao;
import org.apache.openmeetings.db.dao.basic.ConfigurationDao;
import org.apache.openmeetings.db.dao.calendar.AppointmentDao;
import org.apache.openmeetings.db.dao.calendar.MeetingMemberDao;
import org.apache.openmeetings.db.dao.file.FileExplorerItemDao;
import org.apache.openmeetings.db.dao.record.RecordingDao;
import org.apache.openmeetings.db.dao.room.PollDao;
import org.apache.openmeetings.db.dao.room.RoomDao;
import org.apache.openmeetings.db.dao.room.RoomGroupDao;
import org.apache.openmeetings.db.dao.server.LdapConfigDao;
import org.apache.openmeetings.db.dao.server.OAuth2Dao;
import org.apache.openmeetings.db.dao.server.ServerDao;
import org.apache.openmeetings.db.dao.user.GroupDao;
import org.apache.openmeetings.db.dao.user.PrivateMessageDao;
import org.apache.openmeetings.db.dao.user.PrivateMessageFolderDao;
import org.apache.openmeetings.db.dao.user.UserContactDao;
import org.apache.openmeetings.db.dao.user.UserDao;
import org.apache.openmeetings.db.entity.basic.ChatMessage;
import org.apache.openmeetings.db.entity.basic.Configuration;
import org.apache.openmeetings.db.entity.calendar.Appointment;
import org.apache.openmeetings.db.entity.file.FileExplorerItem;
import org.apache.openmeetings.db.entity.record.Recording;
import org.apache.openmeetings.db.entity.room.Room;
import org.apache.openmeetings.db.entity.room.RoomPoll;
import org.apache.openmeetings.db.entity.server.LdapConfig;
import org.apache.openmeetings.db.entity.user.Group;
import org.apache.openmeetings.db.entity.user.PrivateMessage;
import org.apache.openmeetings.db.entity.user.State;
import org.apache.openmeetings.db.entity.user.User;
import org.apache.openmeetings.db.entity.user.User.Salutation;
import org.apache.openmeetings.util.OmFileHelper;
import org.red5.logging.Red5LoggerFactory;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.convert.Registry;
import org.simpleframework.xml.convert.RegistryStrategy;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.Strategy;
import org.simpleframework.xml.stream.Format;
import org.simpleframework.xml.stream.NodeBuilder;
import org.simpleframework.xml.stream.OutputNode;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 
 * @author sebastianwagner
 * 
 */
public class BackupExport {
	private static final Logger log = Red5LoggerFactory.getLogger(BackupExport.class, webAppRootKey);
	private static final String BACKUP_COMMENT = 
			"###############################################\n"
			+ "This File is auto-generated by the Backup Tool \n"
			+ "you should use the BackupPanel to modify or change this file \n"
			+ "see http://openmeetings.apache.org/Upgrade.html for Details \n"
			+ "###############################################\n";

	@Autowired
	private AppointmentDao appointmentDao;
	@Autowired
	private FileExplorerItemDao fileExplorerItemDao;
	@Autowired
	private RecordingDao recordingDao;
	@Autowired
	private UserDao userDao;
	@Autowired
	private MeetingMemberDao meetingMemberDao;
	@Autowired
	private LdapConfigDao ldapConfigDao;
	@Autowired
	private PrivateMessageDao privateMessageDao;
	@Autowired
	private PrivateMessageFolderDao privateMessageFolderDao;
	@Autowired
	private UserContactDao userContactDao;
	@Autowired
	private PollDao pollManager;
	@Autowired
	private ConfigurationDao configurationDao;
	@Autowired
	private ChatDao chatDao;
	@Autowired
	private OAuth2Dao auth2Dao;
	@Autowired
	private ServerDao serverDao;
	@Autowired
	private GroupDao groupDao;
	@Autowired
	private RoomDao roomDao;
	@Autowired
	private RoomGroupDao roomGroupDao;

	public void performExport(File filePath, File backup_dir, boolean includeFiles, ProgressHolder progressHolder) throws Exception {
		if (!backup_dir.exists()) {
			backup_dir.mkdirs();
		}
		Serializer simpleSerializer = new Persister();
		
		progressHolder.setProgress(0);
		/*
		 * ##################### Backup Groups
		 */
		writeList(simpleSerializer, backup_dir, "organizations.xml", "organisations", groupDao.get(0, Integer.MAX_VALUE));
		progressHolder.setProgress(5);
		/*
		 * ##################### Backup Users
		 */
		exportUsers(backup_dir, userDao.getAllBackupUsers());
		progressHolder.setProgress(10);

		/*
		 * ##################### Backup Room
		 */
		{
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			registry.bind(User.class, UserConverter.class);
			registry.bind(Room.Type.class, RoomTypeConverter.class);
			
			writeList(serializer, backup_dir, "rooms.xml", "rooms", roomDao.get());
			progressHolder.setProgress(15);
		}

		/*
		 * ##################### Backup Room Groups
		 */
		{
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			registry.bind(Group.class, GroupConverter.class);
			registry.bind(Room.class, RoomConverter.class);
			
			writeList(serializer, backup_dir, "rooms_organisation.xml", "room_organisations", roomGroupDao.get());
			progressHolder.setProgress(20);
		}

		/*
		 * ##################### Backup Appointments
		 */
		{
			List<Appointment> list = appointmentDao.get();
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			registry.bind(User.class, UserConverter.class);
			registry.bind(Appointment.Reminder.class, AppointmentReminderTypeConverter.class);
			registry.bind(Room.class, RoomConverter.class);
			if (list != null && list.size() > 0) {
				registry.bind(list.get(0).getStart().getClass(), DateConverter.class);
			}
			
			writeList(serializer, backup_dir, "appointements.xml", "appointments", list);
			progressHolder.setProgress(25);
		}

		/*
		 * ##################### Backup Meeting Members
		 */
		{
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			registry.bind(User.class, UserConverter.class);
			registry.bind(Appointment.class, AppointmentConverter.class);
			
			writeList(serializer, backup_dir, "meetingmembers.xml",
					"meetingmembers", meetingMemberDao.getMeetingMembers());
			progressHolder.setProgress(30);
		}

		/*
		 * ##################### LDAP Configs
		 */
		List<LdapConfig> ldapList = ldapConfigDao.get();
		if (!ldapList.isEmpty()) {
			ldapList.remove(0);
		}
		writeList(simpleSerializer, backup_dir, "ldapconfigs.xml", "ldapconfigs", ldapList);
		progressHolder.setProgress(35);

		/*
		 * ##################### Cluster servers
		 */
		writeList(simpleSerializer, backup_dir, "servers.xml", "servers", serverDao.get(0, Integer.MAX_VALUE));
		progressHolder.setProgress(40);

		/*
		 * ##################### OAuth2 servers
		 */
		writeList(simpleSerializer, backup_dir, "oauth2servers.xml", "oauth2servers", auth2Dao.get(0, Integer.MAX_VALUE));
		progressHolder.setProgress(45);

		/*
		 * ##################### Private Messages
		 */
		{
			List<PrivateMessage> list = privateMessageDao.get(0, Integer.MAX_VALUE);
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			registry.bind(User.class, UserConverter.class);
			registry.bind(Room.class, RoomConverter.class);
			if (list != null && list.size() > 0) {
				registry.bind(list.get(0).getInserted().getClass(), DateConverter.class);
			}
			
			writeList(serializer, backup_dir, "privateMessages.xml",
					"privatemessages", list);
			progressHolder.setProgress(50);
		}

		/*
		 * ##################### Private Message Folders
		 */
		writeList(simpleSerializer, backup_dir, "privateMessageFolder.xml",
				"privatemessagefolders", privateMessageFolderDao.get(0, Integer.MAX_VALUE));
		progressHolder.setProgress(55);

		/*
		 * ##################### User Contacts
		 */
		{
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			registry.bind(User.class, UserConverter.class);
			
			writeList(serializer, backup_dir, "userContacts.xml",
					"usercontacts", userContactDao.get());
			progressHolder.setProgress(60);
		}

		/*
		 * ##################### File-Explorer
		 */
		{
			List<FileExplorerItem> list = fileExplorerItemDao.get();
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			if (list != null && list.size() > 0) {
				registry.bind(list.get(0).getInserted().getClass(), DateConverter.class);
			}
			
			writeList(serializer, backup_dir, "fileExplorerItems.xml",
					"fileExplorerItems", list);
			progressHolder.setProgress(65);
		}

		/*
		 * ##################### Recordings
		 */
		{
			List<Recording> list = recordingDao.get();
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			if (list != null && list.size() > 0) {
				registry.bind(list.get(0).getInserted().getClass(), DateConverter.class);
			}
			
			writeList(serializer, backup_dir, "flvRecordings.xml", "flvrecordings", list);
			progressHolder.setProgress(70);
		}

		/*
		 * ##################### Polls
		 */
		{
			List<RoomPoll> list = pollManager.get();
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			registry.bind(User.class, UserConverter.class);
			registry.bind(Room.class, RoomConverter.class);
			registry.bind(RoomPoll.Type.class, PollTypeConverter.class);
			if (list != null && list.size() > 0) {
				registry.bind(list.get(0).getCreated().getClass(), DateConverter.class);
			}
			
			writeList(serializer, backup_dir, "roompolls.xml", "roompolls", list);
			progressHolder.setProgress(75);
		}

		/*
		 * ##################### Config
		 */
		{
			List<Configuration> list = configurationDao.getConfigurations(0, Integer.MAX_VALUE, "c.id", true);
			Registry registry = new Registry();
			registry.bind(State.class, StateConverter.class);
			registry.bind(User.class, UserConverter.class);
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			if (list != null && list.size() > 0) {
				registry.bind(list.get(0).getInserted().getClass(), DateConverter.class);
			}
			
			writeList(serializer, backup_dir, "configs.xml", "configs", list);
			progressHolder.setProgress(80);
		}
		
		/*
		 * ##################### Chat
		 */
		{
			Registry registry = new Registry();
			Strategy strategy = new RegistryStrategy(registry);
			Serializer serializer = new Persister(strategy);
	
			registry.bind(User.class, UserConverter.class);
			registry.bind(Room.class, RoomConverter.class);
			List<ChatMessage> list = chatDao.get(0, Integer.MAX_VALUE);
			if (list != null && list.size() > 0) {
				registry.bind(list.get(0).getSent().getClass(), DateConverter.class);
			}
			
			writeList(serializer, backup_dir, "chat_messages.xml", "chat_messages", list);
			progressHolder.setProgress(85);
		}
		if (includeFiles) {
			/*
			 * ##################### Backup Room Files
			 */
			File targetRootDir = new File(backup_dir, "roomFiles");

			if (!targetRootDir.exists()) {
				targetRootDir.mkdir();
			}

			File sourceDir = OmFileHelper.getUploadDir();

			File[] files = sourceDir.listFiles();
			for (File file : files) {
				if (file.isDirectory()) {
					if (!file.getName().equals("backup") && !file.getName().equals("import")) {
						log.debug("### " + file.getName());

						FileHelper.copyRec(file, new File(targetRootDir, file.getName()));
					}
				}
			}

			/*
			 * ##################### Backup Recording Files
			 */
			File targetDirRec = new File(backup_dir, "recordingFiles");

			if (!targetDirRec.exists()) {
				targetDirRec.mkdir();
			}

			File sourceDirRec = OmFileHelper.getStreamsHibernateDir();

			FileHelper.copyRec(sourceDirRec, targetDirRec);
			progressHolder.setProgress(90);
		}

		writeZipDir(backup_dir, filePath);
		progressHolder.setProgress(100);
		log.debug("---Done");
	}
	
	private <T> void writeList(Serializer ser, File backup_dir, String fileName, String listElement, List<T> list) throws Exception {
		FileOutputStream fos = new FileOutputStream(new File(backup_dir, fileName));
		writeList(ser, fos, listElement, list);
	}
	
	private <T> void writeList(Serializer ser, OutputStream os, String listElement, List<T> list) throws Exception {
		Format format = new Format("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		OutputNode doc = NodeBuilder.write(new OutputStreamWriter(os, StandardCharsets.UTF_8), format);
		OutputNode root = doc.getChild("root");
		root.setComment(BACKUP_COMMENT);
		OutputNode listNode = root.getChild(listElement);

		if (list != null) {
			for (T t : list) {
				try {
					ser.write(t, listNode);
				} catch (Exception e) {
					log.debug("Exception While writing node of type: " + t.getClass(), e);
				}
			}
		}
		root.commit();
	}

	public void exportUsers(File backup_dir, List<User> list) throws Exception {
		FileOutputStream fos = new FileOutputStream(new File(backup_dir, "users.xml"));
		exportUsers(fos, list);
	}
	
	public void exportUsers(OutputStream os, List<User> list) throws Exception {
		Registry registry = new Registry();
		Strategy strategy = new RegistryStrategy(registry);
		Serializer serializer = new Persister(strategy);

		registry.bind(Group.class, GroupConverter.class);
		registry.bind(State.class, StateConverter.class);
		registry.bind(Salutation.class, SalutationConverter.class);
		if (list != null && list.size() > 0) {
			Class<?> dateClass = list.get(0).getRegdate() != null ? list.get(0).getRegdate().getClass() : list.get(0).getInserted().getClass();
			registry.bind(dateClass, DateConverter.class);
		}
		
		writeList(serializer, os, "users", list);
	}

	private void writeZipDir(File directoryToZip, File zipFile) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(zipFile); ZipOutputStream zos = new ZipOutputStream(fos)) {
			writeZipDir(directoryToZip.toURI(), directoryToZip, zos, zipFile);
		}
	}
	
	private void writeZipDir(URI base, File dir, ZipOutputStream zos, File zipFile) throws IOException {
		for (File file : dir.listFiles()) {
			if (zipFile.equals(file)) {
				continue;
			}
			if (file.isDirectory()) {
				writeZipDir(base, file, zos, zipFile);
			} else {
				String path = base.relativize(file.toURI()).toString();
				log.debug("Writing '" + path + "' to zip file");
				ZipEntry zipEntry = new ZipEntry(path);
				zos.putNextEntry(zipEntry);

				OmFileHelper.copyFile(file, zos);
				zos.closeEntry();
			}
		}
	}
}
