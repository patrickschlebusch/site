/* 
 * Copyright 2015 EuregJUG.
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

create table posts (
    id                 serial primary key,
    published_on       date not null,
    slug               varchar(512) not null,
    title              varchar(512) not null,
    content            text not null,    
    format	       varchar(16) not null default 'asciidoc',
    created_at         timestamp not null,
    updated_at         timestamp not null,
    CONSTRAINT posts_uk UNIQUE (published_on, slug)
);
