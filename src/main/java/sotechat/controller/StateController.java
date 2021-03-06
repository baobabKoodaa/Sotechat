package sotechat.controller;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.security.Principal;

import sotechat.data.Session;
import sotechat.data.SessionRepo;
import sotechat.service.QueueService;
import sotechat.service.ValidatorService;
import sotechat.wrappers.ProStateResponse;
import sotechat.wrappers.UserStateResponse;

/**
 * Reititys tilaan liittyville pyynnoille (GET, POST, WS).
 */
@RestController
public class StateController {

    /**
     * Pyyntojen validointi.
     */
    private final ValidatorService validatorService;

    /**
     * Sessioiden kasittely.
     */
    private final SessionRepo sessionRepo;

    /**
     * Jonon kasittely.
     */
    private final QueueService queueService;

    /**
     * Jonon tiedottaminen.
     */
    private final QueueBroadcaster queueBroadcaster;

    /**
     * Sanomien lahettaminen.
     */
    private final MessageBroker broker;


    /**
     * Konstruktori.
     *
     * @param pValidatorService validatorService
     * @param pSessionRepo sessionRepo
     * @param pQueueService queueService
     * @param pQueueBroadcaster queueBroadCaster
     * @param pBroker broker
     */
    @Autowired
    public StateController(final ValidatorService pValidatorService,
                           final SessionRepo pSessionRepo,
                           final QueueService pQueueService,
                           final QueueBroadcaster pQueueBroadcaster,
                           final MessageBroker pBroker) {
        this.validatorService = pValidatorService;
        this.sessionRepo = pSessionRepo;
        this.queueService = pQueueService;
        this.queueBroadcaster = pQueueBroadcaster;
        this.broker = pBroker;
    }

    /**
     * Kun asiakaskayttaja haluaa pyytaa omat tilatietonsa
     * (muun muassa sivun latauksen yhteydessa).
     *
     * @param req Pyynnon tiedot.
     * @param professional Kirjautumistiedot.
     * @return JSON-muotoon paketoitu UserStateResponse
     * lahetetaan pyynnon tehneelle kayttajalle.
     */
    @RequestMapping(value = "/userState", method = RequestMethod.GET)
    public final UserStateResponse returnUserStateResponse(
            final HttpServletRequest req,
            final Principal professional) {
        Session session = sessionRepo.updateSession(req, professional);
        return new UserStateResponse(session);
    }

    /**
     * Kun ammattilaiskayttaja haluaa pyytaa omat tilatietonsa
     * (muun muassa sivun latauksen yhteydessa).
     *
     * @param req Pyynnon tiedot.
     * @param professional Kirjautumistiedot.
     * @return JSON-muotoon paketoitu ProStateResponse (tai tyhja vastaus)
     * lahetetaan pyynnon tehneelle kayttajalle.
     */
    @RequestMapping(value = "/proState", method = RequestMethod.GET)
    public final ProStateResponse returnProStateResponse(
            final HttpServletRequest req,
            final Principal professional) {
        if (professional == null) {
            /** Hacking attempt? */
            return null;
        }
        Session session = sessionRepo.updateSession(req, professional);
        return new ProStateResponse(session);
    }

    /**
     * Kun client lahettaa avausviestin ja haluaa liittya jonoon.
     *
     * @param request Pyynto.
     * @param professional Kirjautumistiedot.
     * @return JSON {"content":"Denied..."} tai {"content":"OK..."}
     * lahetetaan pyynnon tehneelle kayttajalle.
     */
    @RequestMapping(value = "/joinQueue", method = RequestMethod.POST)
    public final String respondToJoinQueueRequest(
            final HttpServletRequest request,
            final Principal professional
    ) {
        String response = processJoinQueueReq(request, professional);
        return "{\"content\":\"" + response + "\"}";
    }

    /**
     * Validoi pyynto liittya jonoon ja suorita se.
     *
     * @param req Pyynto.
     * @param auth Kirjautumistiedot.
     * @return String "OK..." tai "Denied...".
     */
    private String processJoinQueueReq(final HttpServletRequest req,
                                       final Principal auth) {

        /* Tehdaan JSON-objekti clientin lahettamasta JSONista. */
        JsonObject payload;
        try {
            String jsonString = req.getReader().readLine();
            JsonParser parser = new JsonParser();
            payload = parser.parse(jsonString).getAsJsonObject();
        } catch (Exception e) {
            return "Denied due to invalid JSON formatting.";
        }

        /* Validointi. */
        String error = validatorService.validateJoin(req, payload, auth);
        if (!error.isEmpty()) {
            return error;
        }

        /* Suorittaminen. */
        queueService.joinQueue(req, payload);
        queueBroadcaster.broadcastQueue();
        return "OK, please request new state now.";
    }

    /** Kun hoitaja yrittaa ottaa jonosta uuden chatin, client lahettaa
     * tilauspyynnon polkuun /queue/{channelId}/ ja tama metodi aktivoituu.
     *
     *  Toimenpiteet mita tehdaan:
     *  - Poistetaan jonosta kyseinen chatti.
     *  - Broadcastataan jonon uusi tila hoitajille
     *  - Kerrotaan kaikille asianomaisille (/queue/id/ subscribaajille),
     *    kenelle hoitajalle kanava kuuluu. Huomaa palautusarvon selitys!
     *
     * @param channelId channelId
     * @param accessor accessor
     * @return Palautusarvo lahetetaan JSONina polkuun /queue/{channelId}.
     *          esim. {"channel assigned to":"Hoitaja Anne"}
     *          Kayttotapauksia viestille:
     *          - Kerrotaan jonottajalle, etta chatti on auki
     *          - Poppausta yrittanyt client saa kuulla, etta poppaus onnistui
     *          - Poppausta yrittanyt client saa kuulla, etta joku toinen ehti
     *              juuri popata ennen meita.
     */
    @MessageMapping("/toServer/queue/{channelId}")
    @SendTo("/toClient/queue/{channelId}")
    public final String popClientFromQueue(
            final @DestinationVariable String channelId,
            final SimpMessageHeaderAccessor accessor) {

        /* Varmista, etta poppaaja on autentikoitunut. */
        if (accessor.getUser() == null) {
            System.out.println("Hacking attempt?");
            return "";
        }

        /* Yritetaan popata ja haetaan username kenelle kanava on popattu. */
        String assignee = queueService.popQueue(channelId, accessor);

        /* Broadcastataan jonon tila kaikille ammattilaisille.
         * HUOM: Ei broadcastata viesteja viela. Vasta kun joku subscribaa. */
        queueBroadcaster.broadcastQueue();

        /* Palautetaan asianomaisille tieto, kenelle kanava on popattu. */
        return "{\"channelAssignedTo\":\"" + assignee + "\"}";
    }

    /**
     * Pyynto poistua chat-kanavalta (asiakas- tai ammattilaiskayttaja).
     * (Jos asiakaskayttaja on poistunut, halutaan jattaa kanavava suljettuna
     * nakyviin ammattilaisen valilehtiin.)
     *
     * @param req Pyynto.
     * @param pro Kirjautumistiedot.
     * @param channelId Kanavatunnus.
     */
    @RequestMapping(value = "/leave/{channelId}", method = RequestMethod.POST)
    public final void leaveChat(final @PathVariable String channelId,
                                final HttpServletRequest req,
                                final Principal pro) {
        String sessionId = req.getSession().getId();
        if (!validatorService.validateLeave(sessionId, pro, channelId)) {
            return;
        }
        sessionRepo.leaveChannel(channelId, sessionId);
        broker.sendClosedChannelNotice(channelId);
    }

    /**
     * Pyynto asettaa ammattilaisen online-tilaksi true/false.
     * Esimerkiksi /setStatus/?online=true
     *
     * @param online String "true" tai "false"
     * @param req Pyynto.
     * @param professional Kirjautumistiedot.
     * @return Vastaus pyynnon tekijalle, tyypillisesti {"status":"OK"}.
     */
    @RequestMapping(value = "/setStatus/", method = RequestMethod.POST)
    public final String setStatusOfProUser(final @RequestParam String online,
                                           final HttpServletRequest req,
                                           final Principal professional) {
        String error = validatorService
                .validateOnlineStatusChangeRequest(professional, req, online);
        if (!error.isEmpty()) {
            return jsonifiedResponse(error);
        }
        sessionRepo.setOnlineStatus(req, online);
        broker.sendJoinLeaveNotices(professional, online);
        return jsonifiedResponse("");
    }

    /**
     * Metodi, joka muuntaa ValidatorServicen palauttaman String-muotoisen
     * virheilmoituksen JSON-muotoon, jotta se voidaan lahettaa Clientille
     * ja Clientin on helppo kasitella sita.
     *
     * @param error ValidatorServicen palauttama String-muotoinen virheviesti.
     * @return Palauttaa JSON-muotoisen virheen.
     */
    private String jsonifiedResponse(final String error) {
        if (error.isEmpty()) {
            return "{\"status\":\"OK\"}";
        }
        return "{\"error\": \"" + error + "\"}";
    }

}
