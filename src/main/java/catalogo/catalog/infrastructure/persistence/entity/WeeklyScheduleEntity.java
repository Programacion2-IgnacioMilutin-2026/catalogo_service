package catalogo.catalog.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;

// Entidad de persistencia de un  horario semanal. El id es el de la cátedra.
@Getter
@Setter
@Entity
@Table(name = "weekly_schedules")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyScheduleEntity {
    @Id
    private Long id;
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private int slotDurationMinutes;
    private boolean enabled;
    @ManyToOne(optional = false)
    @JoinColumn(name = "professional_id")
    private ProfessionalEntity professional;
}
