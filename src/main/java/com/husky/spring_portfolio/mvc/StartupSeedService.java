package com.husky.spring_portfolio.mvc;

import com.husky.spring_portfolio.mvc.leaderboard.Leaderboard;
import com.husky.spring_portfolio.mvc.leaderboard.LeaderboardJpaRepository;
import com.husky.spring_portfolio.mvc.person.Person;
import com.husky.spring_portfolio.mvc.person.PersonDetailsService;
import com.husky.spring_portfolio.mvc.person.PersonRole;
import com.husky.spring_portfolio.mvc.person.PersonRoleJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Database seeding for default roles, demo persons, and leaderboard rows.
 * Invoked asynchronously so startup does not block on SQLite/JPA.
 */
@Service
public class StartupSeedService {

    private static final Logger log = LoggerFactory.getLogger(StartupSeedService.class);

    private final PersonDetailsService personService;
    private final PersonRoleJpaRepository roleRepo;
    private final LeaderboardJpaRepository leaderboardJpaRepository;

    public StartupSeedService(PersonDetailsService personService,
                              PersonRoleJpaRepository roleRepo,
                              LeaderboardJpaRepository leaderboardJpaRepository) {
        this.personService = personService;
        this.roleRepo = roleRepo;
        this.leaderboardJpaRepository = leaderboardJpaRepository;
    }

    @Transactional
    public void seedAll() {
        seedRoles();
        seedPersons();
        seedLeaderboard();
    }

    private void seedRoles() {
        PersonRole[] personRoles = PersonRole.init();
        for (PersonRole role : personRoles) {
            PersonRole existingRole = roleRepo.findByName(role.getName());
            if (existingRole != null) {
                continue;
            }
            roleRepo.save(role);
        }
    }

    private void seedPersons() {
        Person[] personArray = Person.init();
        for (Person person : personArray) {
            List<Person> personFound = personService.list(person.getName(), person.getUsername());
            if (personFound.isEmpty()) {
                personService.save(person);
                personService.addRoleToPerson(person.getUsername(), "ROLE_STUDENT");
            } else {
                log.info("Person already exists: {}, {}", person.getName(), person.getUsername());
                personService.addRoleToPerson(person.getUsername(), "ROLE_STUDENT");
            }
        }
        personService.addRoleToPerson(personArray[0].getUsername(), "ROLE_ADMIN");
        personService.addRoleToPerson(personArray[1].getUsername(), "ROLE_ADMIN");
        personService.addRoleToPerson(personArray[2].getUsername(), "ROLE_ADMIN");
        personService.addRoleToPerson(personArray[3].getUsername(), "ROLE_ADMIN");
        personService.addRoleToPerson(personArray[4].getUsername(), "ROLE_ADMIN");
    }

    private void seedLeaderboard() {
        log.info("Initializing leaderboard data...");
        if (leaderboardJpaRepository.count() == 0) {
            leaderboardJpaRepository.save(new Leaderboard("Tay Kim", 100, 2));
            leaderboardJpaRepository.save(new Leaderboard("Ethan Tran", 90, 0));
            leaderboardJpaRepository.save(new Leaderboard("Anthony Bazhenov", 70, 3));
            leaderboardJpaRepository.save(new Leaderboard("Test", 50, 1));
            log.info("Leaderboard data initialized.");
        }
    }
}
