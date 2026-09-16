package com.eastapp.backend.people.service;

import com.eastapp.backend.auth.LoginIdentity;
import com.eastapp.backend.auth.LoginIdentityRepository;
import com.eastapp.backend.auth.security.AuthenticatedUser;
import com.eastapp.backend.common.api.CsvImportResponse;
import com.eastapp.backend.common.api.CsvPreviewResponse;
import com.eastapp.backend.common.csv.CsvSupport;
import com.eastapp.backend.common.error.ApiException;
import com.eastapp.backend.people.Role;
import com.eastapp.backend.people.RoleRepository;
import com.eastapp.backend.people.SystemRole;
import com.eastapp.backend.people.UserAccount;
import com.eastapp.backend.people.UserAccountRepository;
import com.eastapp.backend.people.api.CreateUserRequest;
import com.eastapp.backend.people.api.UpdateUserRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class UserCsvService {
    private static final String FORMAT = "EASTAPP_USER_CSV";
    private static final int VERSION = 1;
    private static final List<String> HEADERS = List.of(
            "eastapp_format", "format_version", "languages", "employee_id", "full_name",
            "phone_e164", "role", "initial_password", "birth_date", "start_date", "end_date", "active"
    );
    private final UserAccountRepository userRepository;
    private final LoginIdentityRepository identityRepository;
    private final RoleRepository roleRepository;
    private final UserAccountService userService;

    public UserCsvService(
            UserAccountRepository userRepository,
            LoginIdentityRepository identityRepository,
            RoleRepository roleRepository,
            UserAccountService userService
    ) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
        this.roleRepository = roleRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public CsvExport exportUsers(AuthenticatedUser principal) {
        requireOwner(principal);
        List<UserAccount> users = userRepository.findAllByTenant_IdOrderByIdentity_FullNameAsc(principal.tenantId())
                .stream()
                .filter(user -> user.getRole().getSystemKey() != SystemRole.ADMIN)
                .filter(user -> principal.systemRole().canView(user.getRole().getSystemKey()))
                .toList();
        try {
            StringWriter writer = new StringWriter();
            writer.write('\ufeff');
            try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.RFC4180.builder()
                    .setHeader(HEADERS.toArray(String[]::new))
                    .setRecordSeparator("\r\n")
                    .get())) {
                for (UserAccount user : users) {
                    printer.printRecord(
                            FORMAT, VERSION, "ENGLISH|CHINESE", user.getEmployeeId(), user.getFullName(),
                            user.getPhoneE164(), user.getRole().getSystemKey().name(), "",
                            date(user.getBirthDate()), date(user.getStartDate()), date(user.getEndDate()), user.isActive()
                    );
                }
            }
            return new CsvExport("eastapp-users-" + LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")) + ".csv",
                    writer.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "USER_CSV_EXPORT_FAILED", "The User CSV could not be generated.");
        }
    }

    @Transactional(readOnly = true)
    public CsvPreviewResponse preview(AuthenticatedUser principal, MultipartFile file) {
        requireOwner(principal);
        return analyse(principal, file).preview();
    }

    @Transactional
    public CsvImportResponse importUsers(AuthenticatedUser principal, MultipartFile file) {
        requireOwner(principal);
        Analysis analysis = analyse(principal, file);
        if (analysis.invalid() > 0) throw CsvSupport.badRequest("USER_CSV_INVALID_ROWS", "Fix every invalid CSV row before importing.");
        for (ParsedUser row : analysis.ready()) {
            var created = userService.createImported(principal, new CreateUserRequest(
                    row.password(), row.fullName(), row.phone(), row.role().getId(), null,
                    row.birthDate(), row.startDate(), row.endDate()
            ), row.employeeId());
            if (!row.active()) {
                userService.update(principal, created.id(), new UpdateUserRequest(
                        row.fullName(), row.phone(), row.role().getId(), false, null,
                        row.birthDate(), row.startDate(), row.endDate()
                ));
            }
        }
        return new CsvImportResponse(analysis.ready().size(), analysis.duplicates());
    }

    private Analysis analyse(AuthenticatedUser principal, MultipartFile file) {
        Map<SystemRole, Role> roles = new EnumMap<>(SystemRole.class);
        roleRepository.findAllByTenant_IdOrderByNameAsc(principal.tenantId())
                .forEach(role -> roles.put(role.getSystemKey(), role));
        Set<String> tenantPhones = new HashSet<>();
        Set<String> tenantEmployeeIds = new HashSet<>();
        userRepository.findAllByTenant_IdOrderByIdentity_FullNameAsc(principal.tenantId())
                .forEach(user -> {
                    tenantPhones.add(user.getPhoneE164());
                    tenantEmployeeIds.add(user.getEmployeeId().toUpperCase(Locale.ROOT));
                });
        Set<String> seen = new HashSet<>();
        Set<String> seenEmployeeIds = new HashSet<>();
        List<ParsedUser> ready = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int invalid = 0;
        int duplicates = 0;
        List<CSVRecord> records = CsvSupport.records(file, HEADERS, FORMAT, VERSION);
        for (CSVRecord record : records) {
            int line = Math.toIntExact(record.getRecordNumber() + 1);
            try {
                String name = CsvSupport.text(record, "full_name");
                if (name.isBlank() || name.length() > 120) throw new IllegalArgumentException("full_name must contain 1 to 120 characters");
                String phone = LoginIdentity.normalisePhone(CsvSupport.text(record, "phone_e164"));
                String employeeId = UserAccount.normaliseEmployeeId(CsvSupport.text(record, "employee_id"));
                if (employeeId.length() > 32) {
                    throw new IllegalArgumentException("employee_id must not exceed 32 characters");
                }
                if (tenantPhones.contains(phone) || tenantEmployeeIds.contains(employeeId)
                        || !seen.add(phone) || !seenEmployeeIds.add(employeeId)) {
                    duplicates++;
                    continue;
                }
                SystemRole systemRole = SystemRole.valueOf(CsvSupport.text(record, "role").toUpperCase(Locale.ROOT));
                Role role = roles.get(systemRole);
                if (role == null || !role.isActive() || systemRole == SystemRole.ADMIN || !principal.systemRole().canAssign(systemRole)) {
                    throw new IllegalArgumentException("role is not assignable: " + systemRole);
                }
                String password = CsvSupport.text(record, "initial_password");
                if (identityRepository.findByPhoneE164(phone).isEmpty() && password.length() < 4) {
                    throw new IllegalArgumentException("initial_password must contain at least 4 characters for a new identity");
                }
                if (password.length() > 128) {
                    throw new IllegalArgumentException("initial_password must not exceed 128 characters");
                }
                LocalDate start = optionalDate(record, "start_date");
                LocalDate end = optionalDate(record, "end_date");
                if (start != null && end != null && end.isBefore(start)) throw new IllegalArgumentException("end_date must not be before start_date");
                boolean active = CsvSupport.bool(record, "active");
                if (systemRole == SystemRole.OWNER && !active) {
                    throw new IllegalArgumentException("an Owner row must remain active");
                }
                ready.add(new ParsedUser(employeeId, name, phone, role, password, optionalDate(record, "birth_date"), start, end,
                        active));
            } catch (RuntimeException exception) {
                invalid++;
                if (errors.size() < CsvSupport.MAX_MESSAGES) errors.add("Row " + line + ": " + exception.getMessage());
            }
        }
        return new Analysis(records.size(), ready, duplicates, invalid, errors);
    }

    private static LocalDate optionalDate(CSVRecord record, String column) {
        String value = CsvSupport.text(record, column);
        if (value.isBlank()) return null;
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException exception) { throw new IllegalArgumentException(column + " must use YYYY-MM-DD"); }
    }
    private static String date(LocalDate value) { return value == null ? "" : value.toString(); }
    private static void requireOwner(AuthenticatedUser principal) {
        if (!principal.isOwner()) throw new ApiException(HttpStatus.FORBIDDEN, "USER_CSV_OWNER_REQUIRED", "Only Owner can import or export Users.");
    }

    public record CsvExport(String fileName, byte[] bytes) {}
    private record ParsedUser(String employeeId, String fullName, String phone, Role role, String password, LocalDate birthDate,
                              LocalDate startDate, LocalDate endDate, boolean active) {}
    private record Analysis(int total, List<ParsedUser> ready, int duplicates, int invalid, List<String> errors) {
        CsvPreviewResponse preview() { return new CsvPreviewResponse(FORMAT, VERSION, total, ready.size(), duplicates, invalid, errors); }
    }
}
