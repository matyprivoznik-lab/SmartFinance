package cz.service;

import cz.entity.User;
import cz.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountModerationService {

    private final UserRepository userRepository;

    public AccountModerationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public boolean setBlocked(User actor, Long targetId, boolean blocked) {
        User target = userRepository.findById(targetId).orElse(null);
        if (target == null || actor == null) {
            return false;
        }
        if (actor.getId().equals(target.getId()) || "ROLE_ADMIN".equals(target.getRole())) {
            return false;
        }
        target.setEnabled(!blocked);
        userRepository.save(target);
        return true;
    }
}
