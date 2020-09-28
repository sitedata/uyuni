/**
 * Copyright (c) 2009--2014 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.rhn.domain.errata.test;

import static java.util.Optional.empty;

import com.redhat.rhn.common.db.datasource.ModeFactory;
import com.redhat.rhn.common.db.datasource.WriteMode;
import com.redhat.rhn.domain.channel.Channel;
import com.redhat.rhn.domain.channel.ChannelFactory;
import com.redhat.rhn.domain.channel.test.ChannelFactoryTest;
import com.redhat.rhn.domain.errata.Errata;
import com.redhat.rhn.domain.errata.ErrataFactory;
import com.redhat.rhn.domain.errata.ErrataFile;
import com.redhat.rhn.domain.errata.Severity;
import com.redhat.rhn.domain.errata.ClonedErrata;
import com.redhat.rhn.domain.org.Org;
import com.redhat.rhn.domain.org.OrgFactory;
import com.redhat.rhn.domain.rhnpackage.Package;
import com.redhat.rhn.domain.rhnpackage.PackageEvr;
import com.redhat.rhn.domain.rhnpackage.PackageEvrFactory;
import com.redhat.rhn.domain.rhnpackage.test.PackageTest;
import com.redhat.rhn.frontend.action.channel.manage.ErrataHelper;
import com.redhat.rhn.manager.errata.test.ErrataManagerTest;
import com.redhat.rhn.testing.BaseTestCaseWithUser;
import com.redhat.rhn.testing.ChannelTestUtils;
import com.redhat.rhn.testing.TestUtils;
import com.redhat.rhn.testing.UserTestUtils;

import com.suse.utils.Opt;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * ErrataFactoryTest
 */
public class ErrataFactoryTest extends BaseTestCaseWithUser {

    public void testAddToChannel()  throws Exception {
        Errata e = ErrataFactoryTest.createTestErrata(user.getOrg().getId());
        //add bugs, keywords, and packages so we have something to work with...
        e.addBug(ErrataManagerTest.createTestBug(42L, "test bug 1"));
        e.addBug(ErrataManagerTest.createTestBug(43L, "test bug 2"));
        e.addPackage(PackageTest.createTestPackage(user.getOrg()));
        e.addKeyword("foo");
        e.addKeyword("bar");
        //save changes
        ErrataFactory.save(e);

        Channel channel = ChannelFactoryTest.createTestChannel(user);
        channel.setOrg(user.getOrg());

        Package errataPack = PackageTest.createTestPackage(user.getOrg());
        Package chanPack = PackageTest.createTestPackage(user.getOrg());
        //we have to set the 2nd package to a different EVR to not violate a
        //      unique constraint
        PackageEvr evr =  PackageEvrFactory.lookupOrCreatePackageEvr("45", "99", "983");
        chanPack.setPackageName(errataPack.getPackageName());
        chanPack.setPackageEvr(evr);

        channel.addPackage(chanPack);
        e.addPackage(errataPack);

        List<Errata> errataList = new ArrayList<Errata>();
        errataList.add(e);
        List<Errata> addedList = ErrataFactory.addToChannel(errataList, channel, user, false);
        Errata added = addedList.get(0);
        assertTrue(channel.getPackages().contains(errataPack));
        List<ErrataFile> errataFile =
            ErrataFactory.lookupErrataFilesByErrataAndFileType(added.getId(), "RPM");
        assertTrue(errataFile.get(0).getPackages().contains(errataPack));

    }

    public void testCreateAndLookupVendorAndUserErrata() throws Exception {
        Errata userErrata = createTestErrata(user.getOrg().getId());
        assertTrue(userErrata instanceof Errata);
        assertNotNull(userErrata.getId());
        assertNotNull(userErrata.getAdvisory());

        //Lookup the user errata
        Errata errata = ErrataFactory.lookupById(userErrata.getId());
        assertTrue(errata instanceof Errata);
        assertEquals(userErrata.getId(), errata.getId());
        assertEquals(userErrata.getAdvisory(), userErrata.getAdvisory());

        List<Errata> erratas = ErrataFactory.lookupVendorAndUserErrataByAdvisoryAndOrg(
                userErrata.getAdvisory(), user.getOrg());

        assertEquals(erratas.size(), 1);
        assertTrue(erratas.stream().allMatch(e -> e.getId().equals(userErrata.getId())));
        assertTrue(erratas.stream().allMatch(e -> e.getAdvisoryName().equals(userErrata.getAdvisoryName())));
        assertTrue(erratas.stream().allMatch(e -> e instanceof Errata));

        //create vendor errata with same name as user errata
        Errata vendorErrata = createTestErrata(null, Optional.of(userErrata.getAdvisory()));
        assertTrue(vendorErrata instanceof Errata);
        assertNotNull(vendorErrata.getId());
        assertNotNull(vendorErrata.getAdvisory());

        //Lookup the vendor errata
        errata = ErrataFactory.lookupById(vendorErrata.getId());
        assertTrue(errata instanceof Errata);
        assertEquals(vendorErrata.getId(), errata.getId());
        assertEquals(vendorErrata.getAdvisory(), errata.getAdvisory());
        assertEquals(vendorErrata.getAdvisory(), userErrata.getAdvisory());

        //Lookup vendor and user errata with the same name
        erratas = ErrataFactory.lookupVendorAndUserErrataByAdvisoryAndOrg(userErrata.getAdvisory(),
                user.getOrg());

        assertEquals(erratas.size(), 2);
        assertTrue(erratas.stream().allMatch(e -> e.getId().equals(vendorErrata.getId())
                || e.getId().equals(userErrata.getId())));
        assertTrue(erratas.stream().allMatch(e -> e.getAdvisoryName().equals(userErrata.getAdvisory())));
        assertTrue(erratas.stream().allMatch(e -> e instanceof Errata));
    }

    public void testCreateAndLookupErrata() throws Exception {
        Errata testErrata = createTestErrata(user.getOrg().getId());
        assertTrue(testErrata instanceof Errata);
        assertNotNull(testErrata.getId());
        Long pubid = testErrata.getId();
        String pubname = testErrata.getAdvisoryName();

        //Lookup the errata
        Errata errata = ErrataFactory.lookupById(pubid);
        assertTrue(errata instanceof Errata);
        assertEquals(pubid, errata.getId());
        errata = ErrataFactory.lookupByAdvisoryAndOrg(pubname, user.getOrg());
        assertTrue(errata instanceof Errata);
        assertEquals(pubname, errata.getAdvisoryName());
    }

    public void testCreateAndLookupErrataNullOrg() throws Exception {
        //create an errata with null Org
        Errata testErrata = createTestErrata(null);
        assertTrue(testErrata instanceof Errata);
        assertNotNull(testErrata.getId());
        Long pubid = testErrata.getId();
        String pubname = testErrata.getAdvisoryName();

        //Lookup the errata by null Org
        Errata errata = ErrataFactory.lookupById(pubid);
        assertTrue(errata instanceof Errata);
        assertEquals(pubid, errata.getId());
        errata = ErrataFactory.lookupByAdvisoryAndOrg(pubname, null);
        assertTrue(errata instanceof Errata);
        assertEquals(pubname, errata.getAdvisoryName());

        //Lookup the errata by user's Org
        errata = ErrataFactory.lookupByAdvisoryAndOrg(pubname, user.getOrg());
        assertNull(errata);
    }

    public void testLastModified() throws Exception {
        Errata testErrata = createTestErrata(user.getOrg().getId());
        testErrata = reload(testErrata);
        assertNotNull(testErrata.getLastModified());
    }

    public void testBugs() throws Exception {
        var e = createTestErrata(user.getOrg().getId());
        assertTrue(e.getBugs() == null || e.getBugs().size() == 0);
        e.addBug(ErrataFactory.createBug(123L, "test bug",
                "https://bugzilla.redhat.com/show_bug.cgi?id=" + (Long) 123L));
        assertEquals(1, e.getBugs().size());
    }

    /**
     * Create an Errata for testing and commit it to the DB.
     * @param orgId the Org who owns this Errata
     * @return Errata created
     * @throws Exception something bad happened
     */
    public static Errata createTestErrata(Long orgId) throws Exception {
        return createTestErrata(orgId, empty());
    }

    /**
     * Create an Errata for testing and commit it to the DB.
     * @param orgId the Org who owns this Errata
     * @param advisory if specified, the advisory name
     * @return Errata created
     * @throws Exception something bad happened
     */
    public static Errata createTestErrata(Long orgId, Optional<String> advisory) throws Exception {
        Errata e = new Errata();
        fillOutErrata(e, orgId, advisory);
        ErrataFactory.save(e);
        return e;
    }

    /**
     * Creates and persists an errata that will be flagged as critical.
     *
     * @param orgId the org under which the errata exists
     * @return created errata
     * @throws Exception if the errata cannot be created
     */
    public static Errata createCriticalTestErrata(Long orgId) throws Exception {
        Errata e = new Errata();
        fillOutErrata(e, orgId, empty());
        e.setAdvisoryType(ErrataFactory.ERRATA_TYPE_SECURITY);
        ErrataFactory.save(e);
        return e;
    }

    private static void fillOutErrata(Errata e, Long orgId, Optional<String> advisory) throws Exception {
        String name = Opt.fold(advisory, () -> "JAVA Test " + TestUtils.randomString(), Function.identity());
        Org org = null;
        if (orgId != null) {
            org = OrgFactory.lookupById(orgId);
            e.setOrg(org);
        }
        e.setAdvisory(name);
        e.setAdvisoryType(ErrataFactory.ERRATA_TYPE_BUG);
        e.setProduct("Red Hat Linux");
        e.setDescription("Test desc ..");
        e.setSynopsis("Test synopsis");
        e.setSolution("Test solution");
        e.setNotes("Test notes for test errata");
        e.setTopic("test topic");
        e.setRefersTo("rhn unit tests");
        e.setUpdateDate(new Date());
        e.setIssueDate(new Date());
        e.setAdvisoryName(name);
        e.setAdvisoryRel(2L);
        e.setLocallyModified(Boolean.FALSE);
        e.addKeyword("keyword");
        Package testPackage = PackageTest.createTestPackage(org);

        ErrataFile ef;
        Set errataFilePackages = new HashSet();
        errataFilePackages.add(testPackage);
        e.addPackage(testPackage);
        ef = ErrataFactory.createErrataFile(ErrataFactory.
                lookupErrataFileType("RPM"),
                    "SOME FAKE CHECKSUM",
                    "test errata file" + TestUtils.randomString(), errataFilePackages);

        e.addFile(ef);
        Severity s = new Severity();
        s.setLabel(Severity.IMPORTANT_LABEL);
        s.setRank(1);
        e.setSeverity(s);
    }

    public static void updateNeedsErrataCache(Long packageId, Long serverId,
            Long errataId) {
        WriteMode m =
            ModeFactory.
                getWriteMode("test_queries", "insert_into_rhnServerNeededCache");
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("package_id", packageId);
        params.put("server_id", serverId);
        params.put("errata_id", errataId);
        m.executeUpdate(params);
    }

    public static void testLookupByOriginal() throws Exception {
        Long orgId = UserTestUtils.createOrg("testOrgLookupByOriginal");
        Org org = OrgFactory.lookupById(orgId);
        Errata testErrata = createTestErrata(orgId);

        Long ceid = ErrataHelper.cloneErrataFaster(testErrata.getId(), org);

        List list = ErrataFactory.lookupByOriginal(org, testErrata);

        assertEquals(1, list.size());
        var clone = (ClonedErrata) list.get(0);
        assertTrue(clone.getOriginal().equals(testErrata));
    }

    public void testListErrataChannelPackages() {
        try {
            Channel chan = ChannelTestUtils.createBaseChannel(user);
            Errata e = ErrataFactoryTest.createTestErrata(user.getId());
            Package p = PackageTest.createTestPackage(user.getOrg());
            chan.getErratas().add(e);
            chan.getPackages().add(p);
            e.getPackages().add(p);
            ChannelFactory.save(chan);

            chan = TestUtils.saveAndReload(chan);
            e = TestUtils.saveAndReload(e);
            p = TestUtils.saveAndReload(p);


            List<Long> list = ErrataFactory.listErrataChannelPackages(chan.getId(),
                    e.getId());
            assertContains(list, p.getId());

        }
        catch (Exception e) {
            assertTrue(false);
        }
    }

    /**
     * Test listing errata by channel
     *
     * @throws Exception if anything goes wrong
     */
    public void testListErrataByChannel() throws Exception {
        Channel chan = ChannelTestUtils.createBaseChannel(user);
        Errata e = ErrataFactoryTest.createTestErrata(user.getId());
        chan.getErratas().add(e);

        List<Errata> errata = ErrataFactory.listByChannel(user.getOrg(), chan);
        assertEquals(1, errata.size());
        assertEquals(e, errata.iterator().next());
    }
}

