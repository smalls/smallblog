# smallblog

A small basic blog system.


## Getting Started

By default it'll store information in PostgreSQL, so you'll need to have a
server running.

Start by creating a database:

    bash$ createdb smallblog


You'll need to set several environment variables to configure your test system.
I recommend copying env.sh.template to env.sh, and modifying its contents to
fit your system and databaes configuration.  Source the file before starting
the server:

	bash$ . env.sh


Once you've done that, you can create the db schema:

    bash$ lein migrate


If you want to use S3 to store images, you'll need to set your bucket
permissions (which you can do from the AWS web page, bucket properties -> Edit bucket policy.
A policy might look like this:

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

By default, the server starts both Jetty and a NailGun server (for VimClojure).
It's pretty easy to disable NailGun, look at core.clj.  To start the server,
run:

	lein run :server

Or, from a REPL (this won't block):

	(smallblog.core/start-server false)


## Notes

Some postgres commands:

    to connect:
    bash$ psql smallblog -h localhost

    to describe a table:
    psql> \d+ tablename


## License

Copyright (C) 2011 Matt Small

Distributed under the Eclipse Public License, the same as Clojure.
