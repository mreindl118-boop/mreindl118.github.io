# Content model
One JSON file per project in content/projects/. Adding a project = adding a file (and an images folder); layout never changes. Sorted newest-first by "year" then file date.

Project schema:
slug, title, location, year, type (Public realm | Residential | Ecological restoration | Campus | Streetscape | Competition), scale, role, collaborators, software[], cover, summary, sections[{heading, body}], images[{file, caption, drawing_type, scale, software}]

site.json: name, practice, tagline, location, email, phone, linkedin, bio, skills{drafting|modeling|visualization|analysis: [{tool, level}]}, lastUpdated.

projects/index.json lists project slugs; add the new slug there when adding a project file.
