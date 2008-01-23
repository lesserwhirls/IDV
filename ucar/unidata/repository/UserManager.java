/**
 * $Id: TrackDataSource.java,v 1.90 2007/08/06 17:02:27 jeffmc Exp $
 *
 * Copyright 1997-2005 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */



package ucar.unidata.repository;


import ucar.unidata.data.SqlUtil;
import ucar.unidata.ui.ImageUtils;
import ucar.unidata.util.DateUtil;

import ucar.unidata.util.HtmlUtil;
import ucar.unidata.util.HttpServer;
import ucar.unidata.util.IOUtil;
import ucar.unidata.util.LogUtil;
import ucar.unidata.util.Misc;
import ucar.unidata.util.StringUtil;
import ucar.unidata.util.TwoFacedObject;
import ucar.unidata.xml.XmlUtil;

import java.io.File;



import java.io.UnsupportedEncodingException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.sql.PreparedStatement;

import java.sql.ResultSet;

import java.sql.Statement;


import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;




/**
 * Class TypeHandler _more_
 *
 *
 * @author IDV Development Team
 * @version $Revision: 1.3 $
 */
public class UserManager extends RepositoryManager {




    /** _more_ */
    public RequestUrl URL_USER_LOGIN = new RequestUrl(this, "/user/login");



    public RequestUrl URL_USER_LOGOUT = new RequestUrl(this, "/user/logout");

    /** _more_ */
    public RequestUrl URL_USER_SETTINGS = new RequestUrl(this, "/user/settings");

    public RequestUrl URL_USER_CART = new RequestUrl(this, "/user/cart");


    /** _more_ */
    public RequestUrl URL_USER_LIST = new RequestUrl(this, "/user/list", "Users");

    /** _more_          */
    public RequestUrl URL_USER_EDIT = new RequestUrl(this, "/user/edit", "Users");

    public RequestUrl URL_USER_NEW = new RequestUrl(this, "/user/new");


    /** _more_          */
    public static final String ARG_USER_DELETE_CONFIRM = "user.delete.confirm";

    /** _more_          */
    public static final String ARG_USER_DELETE = "user.delete";



    public static final String ARG_USER_CANCEL = "user.cancel";

    public static final String ARG_USER_CHANGE = "user.change";
    public static final String ARG_USER_NEW = "user.new";

    /** _more_          */
    public static final String ARG_USER_ID = "user.id";

    public static final String ARG_USER_NAME = "user.name";
    public static final String ARG_USER_ROLES = "user.roles";

    /** _more_          */
    public static final String ARG_USER_PASSWORD1 = "user.password1";

    /** _more_          */
    public static final String ARG_USER_PASSWORD2 = "user.password2";

    /** _more_          */
    public static final String ARG_USER_EMAIL = "user.email";

    /** _more_          */
    public static final String ARG_USER_QUESTION = "user.question";

    /** _more_          */
    public static final String ARG_USER_ANSWER = "user.answer";

    /** _more_          */
    public static final String ARG_USER_ADMIN = "user.admin";



    /** _more_          */
    boolean requireLogin = true;

    /** _more_ */
    private Hashtable<String, User> userMap = new Hashtable<String, User>();

    private Hashtable userCart = new Hashtable();

    /**
     * _more_
     *
     * @param repository _more_
     */
    public UserManager(Repository repository) {
        super(repository);
        requireLogin    = getRepository().getProperty(PROP_USER_REQUIRELOGIN,
                true);
    }


    /**
     * _more_
     *
     * @param password _more_
     *
     * @return _more_
     */
    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA");
            md.update(password.getBytes("UTF-8"));
            return XmlUtil.encodeBase64(md.digest()).trim();
        } catch (NoSuchAlgorithmException nsae) {
            throw new IllegalStateException(nsae.getMessage());
        } catch (UnsupportedEncodingException uee) {
            throw new IllegalStateException(uee.getMessage());
        }
    }



    /**
     * _more_
     *
     * @param request _more_
     *
     * @return _more_
     */
    protected boolean isRequestOk(Request request) {
        if (requireLogin
                && request.getRequestContext().getUser().getAnonymous()) {
            if ( !request.getRequestPath().startsWith(getRepository().getUrlBase()
                    + "/user/")) {
                return false;
            }
        }
        return true;
    }


    /**
     * _more_
     *
     * @param request _more_
     *
     * @return _more_
     */
    public String makeLoginForm(Request request) {
        StringBuffer sb   = new StringBuffer("<h3>Please login</h3>");
        String       name = request.getString(ARG_USER_NAME, "");
        sb.append(HtmlUtil.form(URL_USER_LOGIN));
        sb.append(HtmlUtil.formTable());
        sb.append(HtmlUtil.formEntry("User:",
                                     HtmlUtil.input(ARG_USER_NAME, name)));
        sb.append(HtmlUtil.formEntry("Password:",
                                     HtmlUtil.password(ARG_USER_PASSWORD1)));
        sb.append(HtmlUtil.formEntry("", HtmlUtil.submit("Login")));

        sb.append("</form>");
        return sb.toString();

    }

    /**
     * _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    protected User getDefaultUser() throws Exception {
        makeUserIfNeeded(new User("default", "Default User", false));
        return findUser("default");
    }

    protected User getAnonymousUser() throws Exception {
        return new User();
    }


    /**
     * _more_
     *
     * @param id _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    protected User findUser(String id) throws Exception {
        return findUser(id, false);
    }

    protected User findUser(String id, boolean userDefaultIfNotFound) throws Exception {
        if (id == null) {
            return null;
        }
        User user = userMap.get(id);
        if (user != null) {
            return user;
        }
        String query = SqlUtil.makeSelect(COLUMNS_USERS,
                                          Misc.newList(TABLE_USERS),
                                          SqlUtil.eq(COL_USERS_ID,
                                              SqlUtil.quote(id)));
        ResultSet results = getRepository().execute(query).getResultSet();
        if ( !results.next()) {
            //            throw new IllegalArgumentException ("Could not find  user id:" + id + " sql:" + query);
            if(userDefaultIfNotFound) {
                return getDefaultUser();
            }
            return null;
        } else {
            user = getUser(results);
        }

        userMap.put(user.getId(), user);
        return user;
    }



    /**
     * _more_
     *
     * @param user _more_
     * @param updateIfNeeded _more_
     *
     * @throws Exception _more_
     */
    protected void makeOrUpdateUser(User user, boolean updateIfNeeded)
            throws Exception {
        if (getRepository().tableContains(user.getId(), TABLE_USERS,
                                     COL_USERS_ID)) {
            if ( !updateIfNeeded) {
                throw new IllegalArgumentException(
                    "Database already contains user:" + user.getId());
            }
            String query = SqlUtil.makeUpdate(TABLE_USERS, COL_USERS_ID,
                               SqlUtil.quote(user.getId()),
                               new String[] { COL_USERS_NAME,
                                              COL_USERS_PASSWORD,
                    COL_USERS_EMAIL, COL_USERS_QUESTION, COL_USERS_ANSWER,
                    COL_USERS_ADMIN }, new String[] {
                        SqlUtil.quote(user.getName()),
                        SqlUtil.quote(user.getPassword()),
                        SqlUtil.quote(user.getEmail()),
                        SqlUtil.quote(user.getQuestion()),
                        SqlUtil.quote(user.getAnswer()), (user.getAdmin()
                    ? "1"
                    : "0") });
            getRepository().execute(query);
            return;
        }

        getRepository().execute(INSERT_USERS, new Object[] {
            user.getId(), user.getName(), user.getEmail(), user.getQuestion(),
            user.getAnswer(), user.getPassword(), new Boolean(user.getAdmin())
        });
    }





    /**
     * _more_
     *
     * @param user _more_
     *
     * @throws Exception _more_
     */
    protected void makeUserIfNeeded(User user) throws Exception {
        if (findUser(user.getId()) == null) {
            makeOrUpdateUser(user, true);
        }
    }

    /**
     * _more_
     *
     * @param user _more_
     *
     * @throws Exception _more_
     */
    protected void deleteUser(User user) throws Exception {
        String query = SqlUtil.makeDelete(TABLE_USERS, COL_USERS_ID,
                                          SqlUtil.quote(user.getId()));
        getRepository().execute(query);
        deleteRoles(user);
    }

    protected void deleteRoles(User user) throws Exception {
        getRepository().execute(SqlUtil.makeDelete(
                                              TABLE_USERROLES,
                                              SqlUtil.eq(COL_USERROLES_USER_ID, SqlUtil.quote(user.getId()))));
    }


    /*
    protected List<String> getRoles(User user) throws Exception {
        if(user.getRoles() == null) {
        }
        }*/


    private boolean checkPasswords(Request request, User user) {
        String password1 = request.getString(ARG_USER_PASSWORD1,"").trim();
        String password2 = request.getString(ARG_USER_PASSWORD2,"").trim();
        if(password1.length()>0) {
            if(!password1.equals(password2)) {
                return false;
            } else {
                user.setPassword(hashPassword(password1));
            }
        }
        return true;
    }


    private void applyState(Request request, User user, boolean doAdmin) throws Exception {
        user.setName(request.getString(ARG_USER_NAME, user.getName()));
        user.setEmail(request.getString(ARG_USER_EMAIL, user.getEmail()));
        user.setQuestion(request.getString(ARG_USER_QUESTION,
                                           user.getQuestion()));
        user.setAnswer(request.getString(ARG_USER_ANSWER,
                                         user.getAnswer()));
        if(doAdmin) {
            if ( !request.defined(ARG_USER_ADMIN)) {
                user.setAdmin(false);
            } else {
                user.setAdmin(request.get(ARG_USER_ADMIN, user.getAdmin()));
            }
            List<String> roles = StringUtil.split(request.getString(ARG_USER_ROLES,""),"\n",true,true);
            deleteRoles(user);
            for(String role: roles) {
                getRepository().execute(INSERT_USERROLES, new Object[] {user.getId(), role});
            }
            user.setRoles(roles);
        }
        makeOrUpdateUser(user, true);
    }


    /**
     * _more_
     *
     * @param request _more_
     * @param user _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    public Result adminUserEdit(Request request) throws Exception {
        String userId = request.getUser();
        User   user   = findUser(userId);
        if (user == null) {
            throw new IllegalArgumentException("Could not find user:"
                    + userId);
        }

        if (request.defined(ARG_USER_DELETE_CONFIRM)) {
            deleteUser(user);
            return new Result(URL_USER_LIST.toString());
        }

        StringBuffer sb = new StringBuffer();


        if (request.defined(ARG_USER_CHANGE)) {
            boolean okToChangeUser = true;
            okToChangeUser = checkPasswords(request, user);
            if(!okToChangeUser) {
                sb.append("Incorrect passwords");
            }

            if(okToChangeUser) {
                applyState(request, user, true);
            }
        }


        sb.append(getRepository().header("User: " + user.getLabel()));
        sb.append("\n<p/>\n");
        sb.append(HtmlUtil.form(URL_USER_EDIT));
        sb.append("\n");
        sb.append(HtmlUtil.hidden(ARG_USER, user.getId()));
        sb.append("\n");
        if (request.defined(ARG_USER_DELETE)) {
            sb.append("Are you sure you want to delete the user?  ");
            sb.append(HtmlUtil.submit("Yes", ARG_USER_DELETE_CONFIRM));
            sb.append(HtmlUtil.space(2));
            sb.append(HtmlUtil.submit("Cancel", ARG_USER_CANCEL));
        } else {
            makeUserForm(request, user,sb,true);
            sb.append(HtmlUtil.formEntry("&nbsp;<p>",""));
            sb.append(
                HtmlUtil.formEntry(
                    "",
                    HtmlUtil.submit("Change User", ARG_USER_CHANGE)
                    + HtmlUtil.space(2)
                    + HtmlUtil.submit("Delete User", ARG_USER_DELETE)));
            sb.append("</table>");
        }
        sb.append("\n</form>\n");
        Result result = new Result("User:" + user.getName(), sb);
        result.putProperty(PROP_NAVSUBLINKS,
                           getRepository().getSubNavLinks(request,
                               getAdmin().adminUrls));
        return result;
    }


    private void makeUserForm(Request request, User user, StringBuffer  sb, boolean includeAdmin) throws Exception {
        sb.append(HtmlUtil.formTable());
        sb.append(HtmlUtil.formEntry("Name:",
                                     HtmlUtil.input(ARG_USER_NAME,
                                                    user.getName())));
        if(includeAdmin) {
            sb.append(HtmlUtil.formEntry("Admin:",
                                         HtmlUtil.checkbox(ARG_USER_ADMIN,
                                                           "true", user.getAdmin())));
            String roles = user.getRolesAsString("\n");
            sb.append(HtmlUtil.formEntry("Roles:",
                                         HtmlUtil.textArea(ARG_USER_ROLES, roles, 5,20)));
        }
        
        sb.append(HtmlUtil.formEntry("Email:",
                                     HtmlUtil.input(ARG_USER_EMAIL,user.getEmail())));
        
        sb.append(HtmlUtil.formEntry("&nbsp;<p>",""));
        
        sb.append(HtmlUtil.formEntry("Password:",
                                     HtmlUtil.password(ARG_USER_PASSWORD1)));
        
        sb.append(HtmlUtil.formEntry("Password Again:",
                                     HtmlUtil.password(ARG_USER_PASSWORD2)));

    }


    /**
     * _more_
     *
     * @param request _more_
     * @param user _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    public Result adminUserNew(Request request) throws Exception {
        String id = "";
        String name = "";
        String email = "";
        String password1 = "";
        String password2 = "";
        boolean admin = false;

        StringBuffer sb = new StringBuffer();

        if (request.exists(ARG_USER_ID)) {
            id = request.getString(ARG_USER_ID,"").trim();
            name = request.getString(ARG_USER_NAME,name).trim();
            email = request.getString(ARG_USER_EMAIL,"").trim();
            password1 = request.getString(ARG_USER_PASSWORD1,"").trim();
            password2 = request.getString(ARG_USER_PASSWORD2,"").trim();
            admin = request.get(ARG_USER_ADMIN,false);
            boolean okToAdd = true;
            if(id.length()==0) {
                okToAdd = false;
                sb.append("Please enter an ID<br>"); 
            } 

            if(password1.length()==0) {
                okToAdd = false;
                sb.append("Invalid password<br>"); 
            } else  if(!password1.equals(password2)) {
                okToAdd = false;
                sb.append("Invalid password<br>"); 
            }

            if(findUser(id)!=null) {
                okToAdd = false;
                sb.append("User with given id already exists<br>"); 
            }

            if(okToAdd) {
                makeOrUpdateUser(new User(id,name,email,"","",hashPassword(password1), admin),false);
                String userEditLink = HtmlUtil.url(URL_USER_EDIT, ARG_USER, id);
                return new Result(userEditLink);
            }
        }



        sb.append(getRepository().header("Create User"));
        sb.append(HtmlUtil.form(URL_USER_NEW));
        sb.append(HtmlUtil.formTable());
        sb.append(HtmlUtil.formEntry("Id:",
                                     HtmlUtil.input(ARG_USER_ID,
                                                    id)));
        sb.append(HtmlUtil.formEntry("Name:",
                                     HtmlUtil.input(ARG_USER_NAME,
                                                    name)));

        
        sb.append(HtmlUtil.formEntry("Admin:",
                                     HtmlUtil.checkbox(ARG_USER_ADMIN,
                                                       "true", admin)));

        sb.append(HtmlUtil.formEntry("Email:",
                                     HtmlUtil.input(ARG_USER_EMAIL,email)));

        sb.append(HtmlUtil.formEntry("Password:",
                                     HtmlUtil.password(ARG_USER_PASSWORD1)));
        
        sb.append(HtmlUtil.formEntry("Password Again:",
                                     HtmlUtil.password(ARG_USER_PASSWORD2)));

        sb.append(
                  HtmlUtil.formEntry("",
                                     HtmlUtil.submit("Create User", ARG_USER_NEW)));
        sb.append("</table>");
        sb.append("\n</form>\n");
        Result result = new Result("New User", sb);
        result.putProperty(PROP_NAVSUBLINKS,
                           getRepository().getSubNavLinks(request,
                               getAdmin().adminUrls));
        return result;
    }


    /**
     * _more_
     *
     * @param request _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    public Result adminUserList(Request request) throws Exception {
        StringBuffer sb = new StringBuffer();
        sb.append(getRepository().header("Users"));
        sb.append(HtmlUtil.form(URL_USER_NEW));
        sb.append(HtmlUtil.submit("New User"));
        sb.append("</form>");

        String query = SqlUtil.makeSelect(COLUMNS_USERS,
                                          Misc.newList(TABLE_USERS));

        SqlUtil.Iterator iter =
            SqlUtil.getIterator(getRepository().execute(query));
        ResultSet  results;

        List<User> users = new ArrayList();
        while ((results = iter.next()) != null) {
            while (results.next()) {
                users.add(getUser(results));
            }
        }
        sb.append("<table>");
        sb.append(HtmlUtil.row(HtmlUtil.cols(HtmlUtil.bold("ID")+HtmlUtil.space(2),
                                             HtmlUtil.bold("Name")+HtmlUtil.space(2),
                                             HtmlUtil.bold("Roles")+HtmlUtil.space(2),
                                             HtmlUtil.bold("Email")+HtmlUtil.space(2),
                                             HtmlUtil.bold("Admin?")+HtmlUtil.space(2))));

        for (User user : users) {
            String userEditLink =  HtmlUtil.href(
                                                 HtmlUtil.url(
                                                              URL_USER_EDIT, ARG_USER,
                                                              user.getId()), user.getId());
            
            String row = 
                (user.getAdmin()?
                 "<tr valign=\"top\" style=\"background-color:#cccccc;\">":
                 "<tr valign=\"top\" >") +
                HtmlUtil.cols(userEditLink, user.getName(), user.getRolesAsString("<br>"),
                              user.getEmail(),
                              "" + user.getAdmin()) +"</tr>";
            sb.append(row);

        }
        sb.append("</table>");
        Result result = new Result("Users", sb);
        result.putProperty(PROP_NAVSUBLINKS,
                           getRepository().getSubNavLinks(request,
                               getAdmin().adminUrls));
        return result;
    }


    /**
     * _more_
     *
     * @param results _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    protected User getUser(ResultSet results) throws Exception {
        int col = 1;
        User user = new User(results.getString(col++), results.getString(col++),
                        results.getString(col++), results.getString(col++),
                        results.getString(col++), results.getString(col++),
                        results.getBoolean(col++));

        String query = SqlUtil.makeSelect(COL_USERROLES_ROLE,
                                        Misc.newList(TABLE_USERROLES),
                                        SqlUtil.eq(COL_USERROLES_USER_ID, SqlUtil.quote(user.getId())));
        Statement stmt = getRepository().execute(query);
        String[] array = SqlUtil.readString(stmt, 1);
        List<String> roles = new ArrayList<String>(Misc.toList(array));
        user.setRoles(roles);
        return user;
    }



    private List<Entry> getCart(Request request) {
        String sessionId = request.getSessionId();
        
        if(sessionId ==null) {
            return new ArrayList<Entry>();
        }
        List<Entry> cart = (List<Entry>) userCart.get(sessionId);
        if(cart==null) {
            cart = new ArrayList<Entry>();
            userCart.put(sessionId, cart);
        }
        return cart;
    }

    private void addToCart(Request request,List<Entry>entries) throws Exception {
        List<Entry> cart = getCart(request);
        for(Entry entry : entries) {
            if(!cart.contains(entry)) {
                cart.add(entry);
            }
        }
    }


    public Result processCart(Request request) throws Exception {
        String action = request.getString(ARG_ACTION,"");
        StringBuffer sb = new StringBuffer();
        if(action.equals(ACTION_CLEAR)) {
            getCart(request).clear();
        } else  if(action.equals(ACTION_ADD)) {
            Entry entry = getRepository().getEntry(request.getId(""), request);
            if(entry == null) {
                throw new IllegalArgumentException(
                                                   "Could not find entry with id:" + request.getId(""));
            }
            if(!getCart(request).contains(entry)) {
                getCart(request).add(entry);
            }
        }

        return showCart(request);
    }



    public Result showCart(Request request) throws Exception {
        StringBuffer sb = new StringBuffer();
        List<Entry> entries = getCart(request);
        sb.append("<h3>User Cart</h3>");
        if(entries.size()==0) {
            sb.append("No entries in cart");
        } else {
            sb.append(HtmlUtil.href(HtmlUtil.url(URL_USER_CART,ARG_ACTION, ACTION_CLEAR),"Clear Cart"));
            boolean haveFrom = request.defined(ARG_FROM);
            if(haveFrom) {
                Entry fromEntry = getRepository().getEntry(request.getString(ARG_FROM,""),request);
                sb.append("<br>Pick an entry  to associate with: " + fromEntry.getName());
            }


            if(!haveFrom) {
                sb.append(HtmlUtil.form(repository.URL_GETENTRIES,
                                        "name=\"getentries\" method=\"post\""));
                sb.append(HtmlUtil.submit("Get selected", "getselected"));
                sb.append(HtmlUtil.submit("Get all", "getall"));
                sb.append(" As: ");
                List outputList =
                    repository.getOutputTypesForEntries(request, entries);
                sb.append(HtmlUtil.select(ARG_OUTPUT, outputList));
            }
            //            sb.append("<br>");
            sb.append("<ul>");
            OutputHandler outputHandler =  getRepository().getOutputHandler(request);
            for(Entry entry: entries) {
                sb.append("<li> ");
                if(haveFrom) {
                    sb.append(HtmlUtil.href(HtmlUtil.url(getRepository().URL_ASSOCIATION_ADD, ARG_FROM, request.getString(ARG_FROM,""),ARG_TO, entry.getId()),
                                            HtmlUtil.img(
                                                         getRepository().fileUrl("/Association.gif"),
                                                         "Create an association")));
                } else {
                    String links =
                        HtmlUtil.checkbox("entry_" + entry.getId(), "true");
                    sb.append(HtmlUtil.hidden("all_" + entry.getId(), "1"));
                    sb.append(links);
                    sb.append(HtmlUtil.href(HtmlUtil.url(URL_USER_CART, ARG_FROM, entry.getId()),
                                            HtmlUtil.img(
                                                         getRepository().fileUrl("/Association.gif"),
                                                         "Create an association")));
                }
                sb.append(HtmlUtil.space(1));
                sb.append(outputHandler.getEntryUrl(entry));
            }
            sb.append("</ul>");
            if(!haveFrom) {
                sb.append("</form>");
            }
        }
        Result result = new Result("User Cart", sb);
        return result;
    }


    public String getUserLinks(Request request) {
        User   user = request.getRequestContext().getUser();
        String userLink;
        String cartEntry = HtmlUtil.href(URL_USER_CART, HtmlUtil.img(
                                                                     getRepository().fileUrl("/Cart.gif"),
                                                                     "Data Cart"));
        if (user.getAnonymous()) {
            userLink =
                "<a href=\"${root}/user/login\" class=\"navlink\">Login</a>";
        } else {
            userLink = 
                HtmlUtil.href(URL_USER_LOGOUT, "Logout", " class=\"navlink\" ")+
                HtmlUtil.space(1) +"|" +HtmlUtil.space(1) +
                HtmlUtil.href(URL_USER_SETTINGS, user.getLabel(), " class=\"navlink\" ")+
                HtmlUtil.space(1);
        }
        return cartEntry + HtmlUtil.space(2) + userLink;
    }


    /**
     * _more_
     *
     * @param request _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    public Result processLogin(Request request) throws Exception {
        StringBuffer sb = new StringBuffer();
        User user = null;
        if (request.exists(ARG_USER_NAME)) {
            String name     = request.getString(ARG_USER_NAME, "");
            String password = request.getString(ARG_USER_PASSWORD1, "");
            password = hashPassword(password);
            String query = SqlUtil.makeSelect(
                                              COLUMNS_USERS,
                                              Misc.newList(TABLE_USERS),
                                              SqlUtil.makeAnd(Misc.newList(
                                                                           SqlUtil.eq(COL_USERS_ID,
                                                                                      SqlUtil.quote(name)),
                                                                           SqlUtil.eq(COL_USERS_PASSWORD,
                                                                                      SqlUtil.quote(password)))));
                                              
            ResultSet results = getRepository().execute(query).getResultSet();
            if (results.next()) {
                user = getUser(results);
                getRepository().setUserSession(request, user);
                return new Result(HtmlUtil.url(getRepository().URL_MESSAGE,ARG_MESSAGE,"You are logged in"));
            } else {
                sb.append("Incorrect user name or password");
            }
        }

        if(user==null) {
            sb.append(makeLoginForm(request));
        }
        return  new Result("Login", sb);
    }



    public Result processLogout(Request request) throws Exception {
        StringBuffer sb = new StringBuffer();
        getRepository().removeUserSession(request);
        sb.append("Logged out");
        sb.append(makeLoginForm(request));
        Result result = new Result("Logout", sb);
        return result;
    }


    /** _more_ */
    public static final String OUTPUT_CART = "user.cart";

    protected void initOutputHandlers() throws Exception {
        OutputHandler outputHandler = new OutputHandler(getRepository()) {
                public boolean canHandle(String output) {
                    return output.equals(OUTPUT_CART);
                }
                protected void getOutputTypesForEntries(Request request,
                                                        List<Entry> entries, List types)
                    throws Exception {
                    types.add(new TwoFacedObject("Cart", OUTPUT_CART));
                }
                public Result outputEntries(Request request, List<Entry> entries)
                    throws Exception {
                    addToCart(request, entries);
                    return showCart(request);
                }

            };

        getRepository().addOutputHandler(outputHandler);
    }


    public List<String> getRoles() throws Exception {
        String[] roles = SqlUtil.readString(getRepository().execute(SqlUtil.makeSelect(
                                                                                      SqlUtil.distinct(COL_USERROLES_ROLE), 
                                                                                      Misc.newList(TABLE_USERROLES))),1);
        return  new ArrayList<String>(Misc.toList(roles));
    }

    /**
     * _more_
     *
     * @param request _more_
     *
     * @return _more_
     *
     * @throws Exception _more_
     */
    public Result processSettings(Request request) throws Exception {
        StringBuffer sb     = new StringBuffer();
        User user = request.getRequestContext().getUser();
        if(user.getAnonymous()) {
            sb.append("You need to be logged in to change user settings");
            sb.append(makeLoginForm(request));
            return new Result("User Settings", sb);
        }

        if(request.exists(ARG_USER_CHANGE)) {
            boolean okToChangeUser =  checkPasswords(request, user);
            if(!okToChangeUser) {
                sb.append("Incorrect passwords");
            } else {
                applyState(request, user, false);
                return new Result(URL_USER_SETTINGS.toString());
            }
        }

        sb.append(HtmlUtil.form(URL_USER_SETTINGS));
        makeUserForm(request, user,sb, false);
        sb.append(HtmlUtil.formEntry("Roles:",user.getRolesAsString("<br>")));

        sb.append(HtmlUtil.formEntry("",HtmlUtil.submit("Change Settings", ARG_USER_CHANGE))); 
        sb.append("</table>");
        sb.append("</form>");

        return new Result("User Settings", sb);
    }




}

