package com.husky.spring_portfolio.mvc.person;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import org.springframework.security.core.userdetails.UserDetails;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.husky.spring_portfolio.mvc.jwt.JwtTokenUtil;

@RestController
@CrossOrigin
@RequestMapping("/api/person")
public class PersonApiController {
    //     @Autowired
    // private JwtTokenUtil jwtGen;
    /*
    #### RESTful API ####
    Resource: https://spring.io/guides/gs/rest-service/
    */

    // Autowired enables Control to connect POJO Object through JPA
    @Autowired
    private PersonJpaRepository repository;

    @Autowired
    private PersonDetailsService personDetailsService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private AccountEmailService accountEmailService;

    /*
    GET List of People
     */
    @GetMapping("/")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Person>> getPeople() {
        return new ResponseEntity<>( repository.findAllByOrderByNameAsc(), HttpStatus.OK);
    }

    /** Must be declared before /{id} or "jwt" is parsed as a numeric id and the request fails. */
    @GetMapping("/jwt")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Person> getAuthenticatedPersonData() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Person person = repository.findByUsername(username);
        return new ResponseEntity<>(person, HttpStatus.OK);
    }

    /**
     * Update the signed-in user's username, email, and/or password.
     * If the username changes, a new JWT is returned so the client can replace the stored token.
     */
    @PutMapping(value = "/self", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateSelf(@RequestBody Map<String, String> body) {
        String authName = SecurityContextHolder.getContext().getAuthentication().getName();
        Person p = repository.findByUsername(authName);
        if (p == null) {
            return new ResponseEntity<>(Map.of("error", "User not found"), HttpStatus.BAD_REQUEST);
        }
        String newEmail = body.get("email");
        String newUsername = body.get("username");
        String newPassword = body.get("password");
        boolean usernameChanged = false;

        if (newEmail != null && !newEmail.isBlank()) {
            String trimmed = newEmail.trim();
            Person byEmail = repository.findByEmail(trimmed);
            if (byEmail != null && !byEmail.getId().equals(p.getId())) {
                return new ResponseEntity<>(Map.of("error", "Email already in use"), HttpStatus.CONFLICT);
            }
            p.setEmail(trimmed);
        }
        if (newUsername != null && !newUsername.isBlank()) {
            String trimmed = newUsername.trim();
            if (!trimmed.equals(p.getUsername())) {
                Person byUser = repository.findByUsername(trimmed);
                if (byUser != null && !byUser.getId().equals(p.getId())) {
                    return new ResponseEntity<>(Map.of("error", "Username already taken"), HttpStatus.CONFLICT);
                }
                p.setUsername(trimmed);
                usernameChanged = true;
            }
        }
        if (newPassword != null && !newPassword.isBlank()) {
            if (newPassword.length() < 5) {
                return new ResponseEntity<>(Map.of("error", "Password must be at least 5 characters"), HttpStatus.BAD_REQUEST);
            }
            p.setPassword(new BCryptPasswordEncoder().encode(newPassword));
        }
        repository.save(p);

        Map<String, Object> out = new HashMap<>();
        out.put("person", p);
        if (usernameChanged) {
            UserDetails ud = personDetailsService.loadUserByUsername(p.getUsername());
            out.put("token", jwtTokenUtil.generateToken(ud));
        }
        return ResponseEntity.ok(out);
    }

    /*
    GET individual Person using ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Person> getPerson(@PathVariable long id) {
        Optional<Person> optional = repository.findById(id);
        if (optional.isPresent()) {  // Good ID
            Person person = optional.get();  // value from findByID
            return new ResponseEntity<>(person, HttpStatus.OK);  // OK HTTP response: status code, headers, and body
        }
        // Bad ID
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);       
    }

    /*
    DELETE individual Person using ID
     */
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Person> deletePerson(@PathVariable long id) {
        Optional<Person> optional = repository.findById(id);
        if (optional.isPresent()) {  // Good ID
            Person person = optional.get();  // value from findByID
            repository.deleteById(id);  // value from findByID
            return new ResponseEntity<>(person, HttpStatus.OK);  // OK HTTP response: status code, headers, and body
        }
        // Bad ID
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST); 
    }
    @DeleteMapping("/delete/self")
    public ResponseEntity<Person> deleteSelf(@RequestBody Person self) {
        Person deletedPerson = repository.findByUsernameAndPassword(self.getUsername(), self.getPassword());
        if (deletedPerson != null) {  // Good ID
            repository.deleteById(deletedPerson.getId());  // value from findByID
            return new ResponseEntity<>(deletedPerson, HttpStatus.OK);  // OK HTTP response: status code, headers, and body
        }
        // Bad ID
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST); 
    }

    /*
    POST Aa record by Requesting Parameters from URI
     */
    @PostMapping( "/post")
    public ResponseEntity<Object> postPerson(@RequestParam("email") String email,
                                             @RequestParam("password") String password,
                                             @RequestParam("name") String name,
                                             @RequestParam("dob") String dobString,
                                             @RequestParam("username") String username) {
        Date dob;
        try {
            dob = new SimpleDateFormat("MM-dd-yyyy").parse(dobString);
        } catch (Exception e) {
            return new ResponseEntity<>(dobString +" error; try MM-dd-yyyy", HttpStatus.BAD_REQUEST);
        }
        String emailTrim = email != null ? email.trim() : "";
        if (repository.findByEmailIgnoreCase(emailTrim).isPresent()) {
            return new ResponseEntity<>(Map.of("error", "Email already registered"), HttpStatus.CONFLICT);
        }
        if (repository.findByUsername(username.trim()) != null) {
            return new ResponseEntity<>(Map.of("error", "Username already taken"), HttpStatus.CONFLICT);
        }
        Person person = new Person(emailTrim, password, name, dob, username.trim());
        personDetailsService.save(person);
        personDetailsService.addRoleToPerson(person.getUsername(), "ROLE_STUDENT");
        return new ResponseEntity<>(Map.of(
                "message", "Account created. You can sign in now.",
                "username", person.getUsername()), HttpStatus.CREATED);
    }

    /*
    Forgot password: send reset link (does not reveal whether email exists)
     */
    @PostMapping(value = "/forgot-password", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body != null ? body.get("email") : null;
        String generic = "If an account exists for that address, we sent password reset instructions.";
        if (email == null || email.isBlank()) {
            return ResponseEntity.ok(Map.of("message", generic));
        }
        Optional<Person> opt = repository.findByEmailIgnoreCase(email.trim());
        if (opt.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", generic));
        }
        Person p = opt.get();
        String token = AccountEmailService.newSecureToken();
        p.setPasswordResetToken(token);
        p.setPasswordResetExpires(Instant.now().plus(1, ChronoUnit.HOURS));
        repository.save(p);
        accountEmailService.sendPasswordResetEmail(p, token);
        return ResponseEntity.ok(Map.of("message", generic));
    }

    /*
    Reset password using token from email
     */
    @PostMapping(value = "/reset-password", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> body) {
        String token = body != null ? body.get("token") : null;
        String newPassword = body != null ? body.get("password") : null;
        if (token == null || token.isBlank() || newPassword == null || newPassword.length() < 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid token or password (min 5 characters)."));
        }
        Optional<Person> opt = repository.findByPasswordResetToken(token);
        if (opt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired reset link."));
        }
        Person p = opt.get();
        if (p.getPasswordResetExpires() != null && Instant.now().isAfter(p.getPasswordResetExpires())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired reset link."));
        }
        p.setPassword(new BCryptPasswordEncoder().encode(newPassword));
        p.setPasswordResetToken(null);
        p.setPasswordResetExpires(null);
        repository.save(p);
        return ResponseEntity.ok(Map.of("message", "Password updated. You can sign in now."));
    }

    /*
    The personSearch API looks across database for partial match to term (k,v) passed by RequestEntity body
     */
    @PostMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> personSearch(@RequestBody final Map<String,String> map) {
        // extract term from RequestEntity
        String term = (String) map.get("term");

        // JPA query to filter on term
        List<Person> list = repository.findByNameContainingIgnoreCaseOrUsernameContainingIgnoreCase(term, term);

        // return resulting list and status, error checking should be added
        return new ResponseEntity<>(list, HttpStatus.OK);
    }

    /*
    The personStats API adds stats by Date to Person table 
    */
    @PostMapping(value = "/setStats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Person> personStats(@RequestBody final Map<String,Object> stat_map) {
        // find ID
        long id=Long.parseLong((String)stat_map.get("id"));  
        Optional<Person> optional = repository.findById((id));
        if (optional.isPresent()) {  // Good ID
            Person person = optional.get();  // value from findByID

            // Extract Attributes from JSON
            Map<String, Object> attributeMap = new HashMap<>();
            for (Map.Entry<String,Object> entry : stat_map.entrySet())  {
                // Add all attribute other thaN "date" to the "attribute_map"
                if (!entry.getKey().equals("date") && !entry.getKey().equals("id"))
                    attributeMap.put(entry.getKey(), entry.getValue());
            }

            // Set Date and Attributes to SQL HashMap
            Map<String, Map<String, Object>> date_map = new HashMap<>();
            date_map.put( (String) stat_map.get("date"), attributeMap );
            person.setStats(date_map);  // BUG, needs to be customized to replace if existing or append if new
            repository.save(person);  // conclude by writing the stats updates

            // return Person with update Stats
            return new ResponseEntity<>(person, HttpStatus.OK);
        }
        // return Bad ID
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST); 
    }
}