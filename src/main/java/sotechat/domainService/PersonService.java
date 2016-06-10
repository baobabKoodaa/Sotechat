package sotechat.domainService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sotechat.domain.Conversation;
import sotechat.domain.Person;
import sotechat.repo.ConversationRepo;
import sotechat.repo.PersonRepo;

import java.util.List;

/**
 * Created by varkoi on 8.6.2016.
 */
@Service
public class PersonService {

    private PersonRepo personRepo;

    @Autowired
    public PersonService(PersonRepo personRepo){
        this.personRepo = personRepo;
    }

    @Transactional
    public boolean addPerson(Person person, String password){
        try {
            person.setPassword(password);
            personRepo.save(person);
            return true;
        }catch(Exception e){
            return false;
        }
    }

    public Person findOne(String personId) throws Exception {
        return personRepo.findOne(personId);
    }

    public List<Person> findAll(){
        return personRepo.findAll();
    }

    @Transactional
    public void delete(String personId) throws Exception {
        personRepo.delete(personId);
    }

    @Transactional
    public boolean changePassword(String personId, String password){
        try {
            Person person = personRepo.findOne(personId);
            person.setPassword(password);
            personRepo.save(person);
            return true;
        }catch(Exception e){
            return false;
        }
    }

    @Transactional
    public boolean changeScreenName(String personId, String newName){
        try {
            Person person = personRepo.findOne(personId);
            person.setScreenName(newName);
            personRepo.save(person);
            return true;
        }catch(Exception e){
            return false;
        }
    }

    /**
     * Palauttaa listan kaikista henkilon keskusteluista, eli listan
     * Conversation oliota, jotka on liitetty parametrina annettua henkilon
     * id:ta vastaavaan Person olioon
     * @param personId henkikon id
     * @return lista henkilon keskusteluista Conversation olioina
     * @throws Exception IllegalArgumentException
     */
    public List<Conversation> personsConversations(String personId)
            throws Exception {
        personRepo.findOne(personId).getConversationsOfPerson();
    }
}
