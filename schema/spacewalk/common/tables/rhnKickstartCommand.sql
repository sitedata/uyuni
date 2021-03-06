--
-- Copyright (c) 2008--2017 Red Hat, Inc.
--
-- This software is licensed to you under the GNU General Public License,
-- version 2 (GPLv2). There is NO WARRANTY for this software, express or
-- implied, including the implied warranties of MERCHANTABILITY or FITNESS
-- FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
-- along with this software; if not, see
-- http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
--
-- Red Hat trademarks are not licensed under GPLv2. No permission is
-- granted to use or replicate Red Hat trademarks that are incorporated
-- in this software or its documentation.
--


CREATE TABLE rhnKickstartCommand
(
    id                  NUMERIC
                            CONSTRAINT rhn_kscommand_id_pk PRIMARY KEY
                            ,
    kickstart_id        NUMERIC NOT NULL
                            CONSTRAINT rhn_kscommand_ksid_fk
                                REFERENCES rhnKSData (id)
                                ON DELETE CASCADE,
    ks_command_name_id  NUMERIC NOT NULL
                            CONSTRAINT rhn_kscommand_kcnid_fk
                                REFERENCES rhnKickstartCommandName (id),
    arguments           VARCHAR(2048),
    created             TIMESTAMPTZ
                            DEFAULT (current_timestamp) NOT NULL,
    modified            TIMESTAMPTZ
                            DEFAULT (current_timestamp) NOT NULL,
    custom_position     NUMERIC
)

;

CREATE INDEX rhn_kscommand_ksid_idx
    ON rhnKickstartCommand (kickstart_id)
    ;

CREATE SEQUENCE rhn_kscommand_id_seq;

