/*
 * Copyright 2016-2017 EuregJUG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.euregjug.site.web;

import com.github.mkopylec.recaptcha.validation.RecaptchaValidator;
import com.github.mkopylec.recaptcha.validation.ValidationResult;
import eu.euregjug.site.config.MailChimpConfig;
import eu.euregjug.site.events.EventEntity;
import eu.euregjug.site.events.EventRepository;
import eu.euregjug.site.events.Registration;
import eu.euregjug.site.events.RegistrationEntity;
import eu.euregjug.site.events.RegistrationService;
import eu.euregjug.site.links.LinkEntity;
import eu.euregjug.site.links.LinkRepository;
import eu.euregjug.site.posts.PostEntity;
import eu.euregjug.site.posts.PostEntity.Status;
import eu.euregjug.site.posts.PostRenderingService;
import eu.euregjug.site.posts.PostRepository;
import static eu.euregjug.site.web.EventsIcalView.ICS_LINEBREAK;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasProperty;
import org.joor.Reflect;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 *
 * @author Michael J. Simons, 2016-07-17
 */
@RunWith(SpringRunner.class)
@ActiveProfiles("test")
@WebMvcTest(
        controllers = IndexController.class,
        secure = false,
        // Need the custom view component and config as well...
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                    EventsIcalView.class,
                    IndexRssView.class,
                    MailChimpConfig.class,
                    PostRenderingService.class // PostRenderService cannot be mocked (again: @Cacheable)
                }
        )
)
@ImportAutoConfiguration(MessageSourceAutoConfiguration.class)
@EnableSpringDataWebSupport // Needed to enable resolving of Pageable and other parameters
public class IndexControllerTest {

    /**
     * Makes the tests locale aware.
     */
    @TestConfiguration
    static class Config {

        @Bean
        public LocaleResolver localeResolver() {
            return new AcceptHeaderLocaleResolver();
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private EventRepository eventRepository;

    @MockBean
    private PostRepository postRepository;

    @MockBean
    private LinkRepository linkRepository;

    @MockBean
    private RecaptchaValidator recaptchaValidator;

    @MockBean
    private RegistrationService registrationService;

    private final List<EventEntity> events;

    private final List<PostEntity> posts;

    private final List<LinkEntity> links;

    public IndexControllerTest() {
        final ZonedDateTime eventDate = ZonedDateTime.of(2016, 7, 7, 19, 0, 0, 0, ZoneId.of("Europe/Berlin"));
        final EventEntity event1 = Reflect.on(
                new EventEntity(GregorianCalendar.from(eventDate), "name-1", "desc-1")
        ).set("id", 23).set("createdAt", GregorianCalendar.from(eventDate)).get();
        event1.setDuration(60);
        event1.setSpeaker("Farin Urlaub");
        event1.setLocation("Am Strand\n4223 Schlaraffenland\n\nirgendwo");

        final ZonedDateTime eventDate2 = ZonedDateTime.of(2016, 11, 22, 18, 0, 0, 0, ZoneId.of("Europe/Berlin"));
        final EventEntity event2 = Reflect.on(
                new EventEntity(GregorianCalendar.from(eventDate2), "name-2", "desc-2")
        ).set("id", 42).set("createdAt", GregorianCalendar.from(eventDate2)).get();
        this.events = Arrays.asList(event1, event2);

        this.posts = new ArrayList<>();
        final PostEntity post = Reflect.on(new PostEntity(Date.from(LocalDate.of(2016, 8, 5).atStartOfDay(ZoneId.systemDefault()).toInstant()), "foo", "foo", "foo")).set("id", 2).get();
        post.setStatus(Status.published);
        this.posts.add(post);
        this.posts.add(Reflect.on(new PostEntity(Date.from(LocalDate.of(2016, 8, 4).atStartOfDay(ZoneId.systemDefault()).toInstant()), "bar", "bar", "bar")).set("id", 1).get());

        this.links = new ArrayList<>();
        this.links.add(new LinkEntity("http://michael-simons.eu", "Michael Simons"));
    }

    @Test
    public void indexShouldWork() throws Exception {
        when(this.eventRepository.findUpcomingEvents()).thenReturn(events);
        when(this.linkRepository.findAllByOrderByTypeAscSortColAscTitleAsc()).thenReturn(links);
        final PageRequest pageRequest = new PageRequest(0, 5, Sort.Direction.DESC, "publishedOn", "createdAt");
        final PageImpl<PostEntity> postsPage = new PageImpl<>(this.posts, pageRequest, 15);
        when(this.postRepository.findAllByStatus(Status.published, pageRequest)).thenReturn(postsPage);

        final Map<LinkEntity.Type, List<LinkEntity>> links = new HashMap<>();
        links.put(LinkEntity.Type.generic, this.links);

        this.mvc
                .perform(get("http://euregjug.eu"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("upcomingEvents", events))
                .andExpect(model().attribute("links", links))
                .andExpect(model().attributeExists("posts"))
                .andExpect(model().attribute("posts", hasProperty("totalElements", equalTo(15L))));
    }

    @Test
    public void postShouldHandleInvalidDate() throws Exception {
        this.mvc
                .perform(
                        get("http://euregjug.eu/{year}/{month}/{day}/{slug}", 2016, 2, 30, "test")
                )
                .andExpect(status().isFound())
                .andExpect(view().name("redirect:/"));
    }

    @Test
    public void eventsShouldWork() throws Exception {
        when(this.eventRepository.findUpcomingEvents()).thenReturn(events);
        this.mvc
                .perform(get("http://euregjug.eu/events.ics").accept("text/calendar"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/calendar"))
                .andExpect(content().string(""
                        + "BEGIN:VCALENDAR" + ICS_LINEBREAK
                        + "VERSION:2.0" + ICS_LINEBREAK
                        + "PRODID:http://www.euregjug.eu/events" + ICS_LINEBREAK
                        + "BEGIN:VEVENT" + ICS_LINEBREAK
                        + "UID:23@euregjug.eu" + ICS_LINEBREAK
                        + "ORGANIZER:EuregJUG" + ICS_LINEBREAK
                        + "DTSTAMP:20160707T170000Z" + ICS_LINEBREAK
                        + "DTSTART:20160707T170000Z" + ICS_LINEBREAK
                        + "DTEND:20160707T180000Z" + ICS_LINEBREAK
                        + "SUMMARY:name-1 (Farin Urlaub)" + ICS_LINEBREAK
                        + "DESCRIPTION:desc-1" + ICS_LINEBREAK
                        + "URL:http://euregjug.eu/register/23" + ICS_LINEBREAK
                        + "LOCATION: Am Strand, 4223 Schlaraffenland, irgendwo" + ICS_LINEBREAK
                        + "END:VEVENT" + ICS_LINEBREAK
                        + "BEGIN:VEVENT" + ICS_LINEBREAK
                        + "UID:42@euregjug.eu" + ICS_LINEBREAK
                        + "ORGANIZER:EuregJUG" + ICS_LINEBREAK
                        + "DTSTAMP:20161122T170000Z" + ICS_LINEBREAK
                        + "DTSTART:20161122T170000Z" + ICS_LINEBREAK
                        + "DTEND:20161122T190000Z" + ICS_LINEBREAK
                        + "SUMMARY:name-2" + ICS_LINEBREAK
                        + "DESCRIPTION:desc-2" + ICS_LINEBREAK
                        + "URL:http://euregjug.eu/register/42" + ICS_LINEBREAK
                        + "END:VEVENT" + ICS_LINEBREAK
                        + "END:VCALENDAR" + ICS_LINEBREAK));

        verify(this.eventRepository).findUpcomingEvents();
        verifyNoMoreInteractions(this.eventRepository);
    }

    @Test
    public void registerFormShouldWork() throws Exception {
        when(this.eventRepository.findOne(23)).thenReturn(Optional.of(this.events.get(0)));

        this.mvc
                .perform(get("http://euregjug.eu/register/{eventId}", 23))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("registered", false))
                .andExpect(model().attribute("event", this.events.get(0)))
                .andExpect(model().attributeExists("registration"));

        verify(this.eventRepository).findOne(23);
        verifyNoMoreInteractions(this.eventRepository);
    }

    @Test
    public void registerFormShouldHandleInvalidEvent() throws Exception {
        when(this.eventRepository.findOne(23)).thenReturn(Optional.empty());

        this.mvc
                .perform(get("http://euregjug.eu/register/{eventId}", 23))
                .andExpect(status().isFound())
                .andExpect(view().name("redirect:/"));

        verify(this.eventRepository).findOne(23);
        verifyNoMoreInteractions(this.eventRepository);
    }

    @Test
    public void feedShouldWork() throws Exception {
        when(this.eventRepository.findUpcomingEvents()).thenReturn(events);
        final PageRequest pageRequest = new PageRequest(1, 5, Sort.Direction.DESC, "publishedOn", "createdAt");
        final PageImpl<PostEntity> postsPage = new PageImpl<>(this.posts, pageRequest, 15);
        when(this.postRepository.findAllByStatus(Status.published, pageRequest)).thenReturn(postsPage);
        when(this.linkRepository.findAllByOrderByTypeAscSortColAscTitleAsc()).thenReturn(new ArrayList<>());

        final ZoneId zoneUtc = ZoneId.of("UTC");
        final SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        final String date1 = df.format(this.posts.get(0).getPublishedOn());
        final String date2 = df.format(this.posts.get(1).getPublishedOn());

        this.mvc
                .perform(
                        get("http://euregjug.eu/feed.rss")
                                .param("page", "1")
                                .locale(Locale.ENGLISH)
                                .accept("application/rss+xml"))
                .andExpect(xpath("/rss/channel/title").string("EuregJUG Maas-Rhine - All things JVM!"))
                .andExpect(xpath("/rss/channel/link").string("http://euregjug.eu"))
                .andExpect(xpath("/rss/channel/description").string("RSS Feed from EuregJUG, the Java User Group for the Euregio Maas-Rhine (Aachen, Maastricht, Liege)."))
                .andExpect(xpath("/rss/channel/pubDate").string(date1))
                .andExpect(xpath("/rss/channel/lastBuildDate").string(date1))
                .andExpect(xpath("/rss/channel/generator").string("https://github.com/EuregJUG-Maas-Rhine/site"))
                .andExpect(xpath("/rss/channel/*[local-name() = 'link' and @rel='previous']/@href").string("http://euregjug.eu/feed.rss?page=0"))
                .andExpect(xpath("/rss/channel/*[local-name() = 'link' and @rel='self']/@href").string("http://euregjug.eu/feed.rss?page=1"))
                .andExpect(xpath("/rss/channel/*[local-name() = 'link' and @rel='next']/@href").string("http://euregjug.eu/feed.rss?page=2"))
                .andExpect(xpath("/rss/channel/*[local-name() = 'link' and @rel='next']/@href").string("http://euregjug.eu/feed.rss?page=2"))
                .andExpect(xpath("/rss/channel/item").nodeCount(2))
                .andExpect(xpath("/rss/channel/item[2]/title").string("bar"))
                .andExpect(xpath("/rss/channel/item[2]/link").string("http://euregjug.eu/2016/8/4/bar"))
                .andExpect(xpath("/rss/channel/item[2]/*[local-name() = 'encoded']").string(containsString("bar")))
                .andExpect(xpath("/rss/channel/item[2]/pubDate").string(date2))
                .andExpect(xpath("/rss/channel/item[2]/author").string("euregjug.eu"))
                .andExpect(xpath("/rss/channel/item[2]/guid").string("http://euregjug.eu/2016/8/4/bar"))
                .andExpect(status().isOk());
    }

    @Test
    public void registerShouldHandleInvalidData() throws Exception {
        when(this.eventRepository.findOne(23)).thenReturn(Optional.of(this.events.get(0)));

        this.mvc.perform(post("/register/{eventId}", 23))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("registered", false))
                .andExpect(model().attribute("event", this.events.get(0)))
                .andExpect(model().attribute("alerts", Arrays.asList("invalidRegistration")))
                .andExpect(model().attributeExists("registration"));
    }

    @Test
    public void registerShouldHandleInvalidRecaptca() throws Exception {
        when(this.eventRepository.findOne(23)).thenReturn(Optional.of(this.events.get(0)));
        when(this.recaptchaValidator.validate(any(HttpServletRequest.class))).thenReturn(new ValidationResult(false, new ArrayList<>()));

        this.mvc.perform(
                post("/register/{eventId}", 23)
                        .param("firstName", "Michael")
                        .param("name", "Simons")
                        .param("email", "michael@euregjug.eu")
        )
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("registered", false))
                .andExpect(model().attribute("event", this.events.get(0)))
                .andExpect(model().attribute("alerts", Arrays.asList("invalidRegistration")))
                .andExpect(model().attributeExists("registration"));
    }

    @Test
    public void registerShouldHandleInvalidRegistration() throws Exception {
        when(this.eventRepository.findOne(23)).thenReturn(Optional.of(this.events.get(0)));
        when(this.recaptchaValidator.validate(any(HttpServletRequest.class))).thenReturn(new ValidationResult(true, new ArrayList<>()));
        when(this.registrationService.register(eq(23), any(Registration.class))).thenThrow(new RegistrationService.InvalidRegistrationException("broken", "broken"));

        this.mvc.perform(
                post("/register/{eventId}", 23)
                        .param("firstName", "Michael")
                        .param("name", "Simons")
                        .param("email", "michael@euregjug.eu")
        )
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attribute("registered", false))
                .andExpect(model().attribute("event", this.events.get(0)))
                .andExpect(model().attribute("alerts", Arrays.asList("broken")))
                .andExpect(model().attributeExists("registration"));
    }

    @Test
    public void registerShouldWork() throws Exception {
        final RegistrationEntity registrationEntity = new RegistrationEntity(this.events.get(0), "michael@euregjug.eu", "Simons", "Michael", false);

        when(this.recaptchaValidator.validate(any(HttpServletRequest.class))).thenReturn(new ValidationResult(true, new ArrayList<>()));
        when(this.registrationService.register(eq(23), any(Registration.class))).thenReturn(registrationEntity);

        this.mvc.perform(
                post("/register/{eventId}", 23)
                        .locale(Locale.GERMAN)
                        .param("firstName", "Michael")
                        .param("name", "Simons")
                        .param("email", "michael@euregjug.eu")
        )
                .andExpect(status().isFound())
                .andExpect(view().name("redirect:/register/23"))
                .andExpect(flash().attribute("registered", true))
                .andExpect(flash().attribute("event", this.events.get(0)))
                .andExpect(flash().attribute("alerts", Arrays.asList("registered")));

        verify(this.registrationService).register(eq(23), any(Registration.class));
        verify(this.registrationService).sendConfirmationMail(registrationEntity, Locale.GERMAN);
        verifyNoMoreInteractions(this.eventRepository, this.registrationService);
    }

    @Test
    public void shouldHandleInvalidPost() throws Exception {
        final Date postDate = Date.from(LocalDate.of(2017, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        when(this.postRepository.findByPublishedOnAndSlug(postDate, "foo")).thenReturn(Optional.empty());

        this.mvc.perform(
                get("/2017/1/1/foo")
        )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isFound())
                .andExpect(view().name("redirect:/"));

        verify(this.postRepository).findByPublishedOnAndSlug(postDate, "foo");
        verifyNoMoreInteractions(this.postRepository);
    }

    @Test
    public void shouldHandleInvalidDate() throws Exception {
        this.mvc.perform(
                get("/2017/1/32/foo")
        )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isFound())
                .andExpect(view().name("redirect:/"));

        verifyZeroInteractions(this.postRepository);
    }

    @Test
    public void shouldDisplayPost() throws Exception {
        final Date postDate = Date.from(LocalDate.of(2017, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        when(this.postRepository.findByPublishedOnAndSlug(postDate, "foo")).thenReturn(Optional.of(this.posts.get(0)));
        final Optional<PostEntity> previousPost = Optional.of(this.posts.get(1));
        when(this.postRepository.getPrevious(this.posts.get(0))).thenReturn(previousPost);
        when(this.postRepository.getNext(this.posts.get(0))).thenReturn(Optional.empty());

        this.mvc.perform(
                get("/2017/1/1/foo")
        )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(view().name("post"))
                .andExpect(model().attribute("previousPost", previousPost))
                .andExpect(model().attributeExists("post"))
                .andExpect(model().attribute("nextPost", Optional.empty()));

        verify(this.postRepository).findByPublishedOnAndSlug(postDate, "foo");
        verify(this.postRepository).getPrevious(this.posts.get(0));
        verify(this.postRepository).getNext(this.posts.get(0));
        verifyNoMoreInteractions(this.postRepository);
    }

    @Test
    public void archiveShouldWork() throws Exception {
        when(this.postRepository.findAll(any(Sort.class))).thenReturn(this.posts);
        this.mvc.perform(
                get("/archive")
        )
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isOk())
                .andExpect(view().name("archive"))
                .andExpect(model().attributeExists("posts"));

        verify(this.postRepository).findAll(any(Sort.class));
        verifyNoMoreInteractions(this.postRepository);

    }
}
