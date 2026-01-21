package com.husky.spring_portfolio.mvc;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.husky.spring_portfolio.mvc.person.Person;
import com.husky.spring_portfolio.mvc.person.PersonDetailsService;
import com.husky.spring_portfolio.mvc.person.PersonRole;
import com.husky.spring_portfolio.mvc.person.PersonRoleJpaRepository;

import java.util.List;

@Component
@Configuration // Scans Application for ModelInit Bean, this detects CommandLineRunner
public class ModelInit {
    @Autowired PersonDetailsService personService;
    @Autowired PersonRoleJpaRepository roleRepo;

    @Bean
    CommandLineRunner run() {  // The run() method will be executed after the application starts
        return args -> {

            PersonRole[] personRoles = PersonRole.init();
            for (PersonRole role : personRoles) {
                PersonRole existingRole = roleRepo.findByName(role.getName());
                if (existingRole != null) {
                    // role already exists
                    continue;
                } else {
                    // role doesn't exist
                    roleRepo.save(role);
                }
            }

            // Person database is populated with test data
            Person[] personArray = Person.init();
            for (Person person : personArray) {
                //findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase
                List<Person> personFound = personService.list(person.getName(), person.getUsername());  // lookup
                if (personFound.size() == 0) {
                    personService.save(person);  // save

                    personService.addRoleToPerson(person.getUsername(), "ROLE_STUDENT");     
                } else {
                    System.out.println("Person already exists: " + person.getName() + ", " + person.getUsername());
                    personService.addRoleToPerson(person.getUsername(), "ROLE_STUDENT");
                }
            }
            personService.addRoleToPerson(personArray[0].getUsername(), "ROLE_ADMIN");
            personService.addRoleToPerson(personArray[1].getUsername(), "ROLE_ADMIN");
            personService.addRoleToPerson(personArray[2].getUsername(), "ROLE_ADMIN");
            personService.addRoleToPerson(personArray[3].getUsername(), "ROLE_ADMIN");
            personService.addRoleToPerson(personArray[4].getUsername(), "ROLE_ADMIN");

        };
    }
}

