# smallblog

This was kind of a work in progress. Everything checked in is working, but I'm
not currently developing this.


A small basic blog system.

Key features include:

- Markdown-formatted posts
- Blogs linked to domains
- Each accounts can have multiple blogs


## Getting Started

By default it'll store information in PostgreSQL, so you'll need to have a
server running.

Start by creating a database:

    bash$ createdb smallblog


You'll need to set several environment variables to configure your test system.
I recommend copying env.sh.template to env.sh, and modifying its contents to
fit your system and database configuration.  Source the file before starting
the server:

	bash$ . env.sh


Once you've done that, you can create the db schema:

    bash$ lein migrate


If you want to use S3 to store images, you'll need to set your bucket
permissions (which you can do from the AWS web page, bucket properties -> Edit bucket policy.
A policy to allow anyone to read anything from your bucket looks like this:

    {
        "Version": "2008-10-17",
        "Statement": [
            {
                "Sid": "AddPerm",
                "Effect": "Allow",
                "Principal": {
                    "AWS": "*"
                },
                "Action": "s3:GetObject",
                "Resource": "arn:aws:s3:::smallblog-test/*"
            }
        ]
    }



## Usage

Running locally just takes one command:

	lein run :server

Or, from a REPL (this won't block):

	(smallblog.core/start-server false)

I use Heroku for my deployment.


### Setup on Heroku

You'll need to enable some addons, my current list:

	> heroku addons
	logging:basic
	pgbackups:auto-month
	releases:basic
	sendgrid:starter
	shared-database:5mb
	ssl:piggyback

And enable environment variables particular for your setup.  See
env.sh.template, or more likely your personal env.sh.


### Upgrading on Heroku

As usual, uploading a new version starts with a git push:

	git push heroku master

You may need to update the remote schema, as well (this command is safe to run
with or without changes):

	heroku run lein run :migrate


## Notes

Notes, mostly for myself.


### Todo

- Analytics
- Form validation
- Better look & feel
DONE- Custom domains (one account can have multiple blogs, each with a unique
		DNS entry)
DONE- Store picture in S3
DONE- Post preview on the edit screen


### Some postgres commands

to connect:

    bash$ psql smallblog -h localhost

to describe a table:

    psql> \d+ tablename


## License

Copyright (C) 2011-2012 Matt Small

Distributed under the Eclipse Public License, the same as Clojure.
