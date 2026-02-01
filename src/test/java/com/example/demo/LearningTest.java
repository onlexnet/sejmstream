package com.example.demo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

public class LearningTest {

    static List<Person> theYoungestOf(List<Person> person, int limit) {

        var list = new ArrayList<>(person);
        Collections.sort(list, new PersonComparator());
        var topThree = list.subList(0, limit);
        return topThree;

    }

    static List<Person> theOldestOf(List<Person> person, int limit) {

        return person.stream().sorted(new PersonComparator().reversed()).limit(limit).toList();

    }

    @Test
    void top3Resturnsx2() {
        var person1 = new Person("Slawuus", 420);
        var person2 = new Person("Asiulek", 28);
        var person3 = new Person("aleczek", 26);

        var list = List.of(person1, person2, person3);

        var actual = theOldestOf(list, 2);

        Assertions.assertThat(actual).isEqualTo(List.of(person1, person2));
    }

    @Test
    void shouldWork() {
    }

    @Test
    void top3Returns() {
        var person1 = new Person("Slawuus", 420);
        var person2 = new Person("Asiulek", 28);
        var person3 = new Person("aleczek", 26);

        var list = List.of(person1, person2, person3);

        var actual = theYoungestOf(list, 2);

        Assertions.assertThat(actual).isEqualTo(List.of(person3, person2));
    }

    @Test
    void shouldDeseralizeNumberFieldInList() {
        record Proceeding(int number, List<LocalDate> dates) {
        }
        var restClient = new RestTemplate();
        var actual = restClient.getForObject("https://api.sejm.gov.pl/sejm/term10/proceedings", Proceeding[].class);
        Assertions.assertThat(actual).allSatisfy(it -> Assertions.assertThat(it.number).isGreaterThan(0));
    }

    @Test
    void shouldDeseralizeNumberFieldInArray() {
        record Proceeding(int number) {
        }
        var restClient = RestClient.create("https://api.sejm.gov.pl");
        var actual = restClient.get().uri("sejm/term10/proceedings").retrieve().body(Proceeding[].class);
        Assertions.assertThat(actual).allSatisfy(it -> Assertions.assertThat(it.number).isGreaterThan(0));
    }
}

class PersonComparator implements Comparator<Person> {

    @Override
    public int compare(Person o1, Person o2) {
        if (o1.age() > o2.age()) {
            return 1;
        } else if (o1.age() < o2.age()) {
            return -1;
        }
        return 0;
    }

}

record Person(String name, int age) {
}
