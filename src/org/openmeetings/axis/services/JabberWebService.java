package org.openmeetings.axis.services;

import java.util.List;

import org.openmeetings.app.OpenmeetingsVariables;
import org.openmeetings.app.data.basic.AuthLevelmanagement;
import org.openmeetings.app.data.basic.Sessionmanagement;
import org.openmeetings.app.data.conference.Invitationmanagement;
import org.openmeetings.app.data.user.Usermanagement;
import org.openmeetings.app.persistence.beans.domain.Organisation_Users;
import org.openmeetings.app.persistence.beans.invitation.Invitations;
import org.openmeetings.app.persistence.beans.rooms.Rooms;
import org.openmeetings.app.persistence.beans.rooms.Rooms_Organisation;
import org.openmeetings.app.persistence.beans.user.Users;
import org.openmeetings.app.remote.ConferenceService;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

public class JabberWebService {
	private static final Logger log = Red5LoggerFactory.getLogger(
			JabberWebService.class, OpenmeetingsVariables.webAppRootKey);

	@Autowired
	private AuthLevelmanagement authLevelManagement;
	@Autowired
	private Usermanagement userManagement;
	@Autowired
	private Sessionmanagement sessionManagement;
	@Autowired
	private ConferenceService conferenceService;
	@Autowired
	private Invitationmanagement invitationManagement;

	public List<Rooms> getAvailableRooms(String SID) {
		log.debug("getAvailableRooms enter");

		List<Rooms> result = this.conferenceService
				.getAppointedMeetingRoomsWithoutType(SID);

		List<Rooms> pbl = this.conferenceService.getRoomsPublicWithoutType(SID);
		if (pbl != null) {
			result.addAll(pbl);
		}

		Long users_id = this.sessionManagement.checkSession(SID);
		Users u = this.userManagement.getUserById(users_id);
		for (Organisation_Users ou : u.getOrganisation_users()) {
			List<Rooms_Organisation> rol = this.conferenceService
					.getRoomsByOrganisationWithoutType(SID, ou
							.getOrganisation().getOrganisation_id().longValue());
			if (rol != null) {
				for (Rooms_Organisation ro : rol) {
					result.add(ro.getRoom());
				}
			}
		}
		for (Rooms r : result) {
			r.setCurrentusers(null);
		}
		return result;
	}

	public int getUserCount(String SID, Long roomId) {
		Long users_id = this.sessionManagement.checkSession(SID);
		Long user_level = this.userManagement.getUserLevelByID(users_id);

		if (this.authLevelManagement.checkUserLevel(user_level)) {
			return this.conferenceService.getRoomClientsMapByRoomId(roomId)
					.size();
		}
		return -1;
	}

	public String getInvitationHash(String SID, String username, Long room_id) {
		Long users_id = this.sessionManagement.checkSession(SID);
		Long user_level = this.userManagement.getUserLevelByID(users_id);
		Invitations invitation = this.invitationManagement.addInvitationLink(
				user_level, username, username, username, username, username,
				room_id, "", Boolean.valueOf(false), null, Integer.valueOf(3),
				null, null, users_id, "", Long.valueOf(1L),
				Boolean.valueOf(false), null, null, null);

		return ((invitation == null) ? null : invitation.getHash());
	}
}
