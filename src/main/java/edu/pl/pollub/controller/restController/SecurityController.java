package edu.pl.pollub.controller.restController;

import com.sun.istack.internal.NotNull;
import edu.pl.pollub.entity.User;
import edu.pl.pollub.entity.VerificationToken;
import edu.pl.pollub.entity.request.UserRegisterRequest;
import edu.pl.pollub.event.OnRegistrationCompleteEvent;
import edu.pl.pollub.exception.AuthException;
import edu.pl.pollub.exception.InvalidRequestException;
import edu.pl.pollub.exception.ObjectNotFoundException;
import edu.pl.pollub.service.MailService;
import edu.pl.pollub.service.UserService;
import edu.pl.pollub.service.VerificationTokenService;
import edu.pl.pollub.validator.UserValidator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.rmi.NoSuchObjectException;
import java.util.Calendar;
import java.util.Locale;

/**
 * Created by Dell on 2017-03-16.
 */
@RestController
public class SecurityController {

    private final ApplicationEventPublisher eventPublisher;

    private final UserService userService;

    private final VerificationTokenService verificationTokenService;

    private final MailService mailService;

    private final UserValidator userValidator;

    @Inject
    public SecurityController(final ApplicationEventPublisher eventPublisher,final MailService mailService, final UserService userService, final VerificationTokenService verificationTokenService, final UserValidator userValidator) {
        this.eventPublisher = eventPublisher;
        this.userService = userService;
        this.mailService = mailService;
        this.verificationTokenService = verificationTokenService;
        this.userValidator = userValidator;
    }

    @RequestMapping(value = "/registration", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public long registration(@RequestBody @Valid UserRegisterRequest userForm, HttpServletRequest request, BindingResult bindingResult) {
        userValidator.validate(userForm, bindingResult);
        if (bindingResult.hasErrors()) {
            throw new InvalidRequestException("Invalid user", bindingResult);
        }
        User user=userService.registerNewUserAccount(new User(userForm));
        eventPublisher.publishEvent(new OnRegistrationCompleteEvent(user, request.getLocale(), request.getContextPath()));
        return user.getId();
    }

    @RequestMapping(value = "/resendToken/{id}", method = RequestMethod.GET)
    public long resendToken(@PathVariable long id, HttpServletRequest request) throws ObjectNotFoundException {
        User user=userService.getById(id);
        VerificationToken newToken=verificationTokenService.generateNewVerificationToken(user);
        verificationTokenService.save(newToken);
        String recipientAddress = user.getEmail();
        String appUrl = request.getContextPath();
        String subject = "Registration Confirmation";
        String confirmationUrl
                = appUrl + "/registration/confirm?token=" + newToken.getToken();
        String message = "Please click this link to verify your account: ";

        mailService.sendMail("from@no-spam.com",recipientAddress,subject,message + "http://localhost:8081" + confirmationUrl);
        return user.getId();
    }

    @RequestMapping(value = "/registration/confirm", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public void confirmRegistration(WebRequest request, @RequestParam("token") String token) throws NoSuchObjectException, AuthException, ObjectNotFoundException {
        Locale locale = request.getLocale();

        VerificationToken verificationToken = verificationTokenService.getByToken(token);
        if (verificationToken == null) {
            throw new AuthException("Your activation token is invalid, please resend it");
        }

        User user = verificationToken.getUser();
        Calendar cal = Calendar.getInstance();
        if ((verificationToken.getDate() - cal.getTime().getTime()) <= 0) {
            throw new AuthException("this token is expired");
        }

        user.setEnabled(true);
        userService.saveRegisteredUser(user);
    }
}
